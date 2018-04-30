package com.google.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GoogleSearch {

    public static void main(String args[]) throws InterruptedException, ExecutionException {

        final String googleSearchUrl = "https://www.google.com/search?q=";
        final String searchWord = args[0];
        final String charset = "UTF-8";

        try {
            URL url = new URL(googleSearchUrl + URLEncoder.encode(searchWord, charset));
            HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.addRequestProperty("User-Agent", "Mozilla/4.76");
            InputStream inputStream = httpConnection.getInputStream();
            List<String> results = searchResults(inputStream);

            int noOfThreads = Runtime.getRuntime().availableProcessors();
            int index = results.size() / noOfThreads;

            List<List<String>> groupedResults = new ArrayList<>(results.stream().collect(Collectors.partitioningBy(e -> results.indexOf(e) > index)).values());

            List<Future<List<String>>> futureList = submitJavaScritSearchRequests(noOfThreads, groupedResults);
            List<String> allReferredJavaScripts = new ArrayList<>();
            futureList.forEach(e -> {
                try {
                    allReferredJavaScripts.addAll(e.get());
                } catch (Exception e3) {
                    e3.printStackTrace();
                }

            });

            Map<String, Long> scriptsMap = allReferredJavaScripts.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            Map<String, Long> sortedMap = scriptsMap.entrySet().stream().sorted(Collections.reverseOrder(Entry.comparingByValue())).collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            List<String> top5Script = sortedMap.entrySet().stream().map(Map.Entry::getKey).limit(5).collect(Collectors.toList());

            top5Script.forEach(e -> {
                System.out.println(e);
            });

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }

        System.exit(0);
    }

    private static List<Future<List<String>>> submitJavaScritSearchRequests(int noOfThreads, List<List<String>> groupedResults) {
        ExecutorService taskExecutor = Executors.newFixedThreadPool(noOfThreads);

        List<Future<List<String>>> futureList = new ArrayList<>();
        groupedResults.stream().forEach(e -> {
            List<String> scriptLibs = new ArrayList<>();
            Future<List<String>> future = taskExecutor.submit(() -> {
                e.forEach(link -> {
                    String page = downloadPage(link);
                    scriptLibs.addAll(javaScriptLibraries(page));
                });
                return scriptLibs;
            });
            futureList.add(future);
        });
        return futureList;
    }

    public static String downloadPage(final String URL) {

        URL myUrl = null;
        BufferedReader in = null;
        String result = null;
        try {
            myUrl = new URL(URL.replaceAll("\"", ""));
            HttpURLConnection httpConnection = (HttpURLConnection) myUrl.openConnection();
            httpConnection.addRequestProperty("User-Agent", "Mozilla/4.76");

            result = convertStreamToString(httpConnection.getInputStream());

        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    private static List<String> javaScriptLibraries(String page) {
        Pattern pattern = Pattern.compile("<script src(.*?)</script>");
        Matcher matcher = pattern.matcher(page);

        List<String> jsLibs = new ArrayList<>();
        while (matcher.find()) {
            String jslib = matcher.group(0);
            jsLibs.add(jslib);
        }
        return jsLibs;
    }

    private static List<String> searchResults(InputStream inputStream) {
        String result = convertStreamToString(inputStream);
        Pattern linkPattern = Pattern.compile("href=\"https:[^>][^\"]*\"");

        List<String> referedLinks = new ArrayList<>();

        Matcher matcher = linkPattern.matcher(result);
        while (matcher.find()) {
            String link = matcher.group(0);
            link = link.replace("href=", "");
            link = link.replace(">", "");
            referedLinks.add(link);
        }
        return referedLinks;
    }

    static String convertStreamToString(java.io.InputStream is) {
        String result = new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
        return result;
    }

}
