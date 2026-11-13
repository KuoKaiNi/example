package com.example.holdings.quote;

/** 抓網址回傳內容；抽出來是為了測試時不必真的連網。 */
@FunctionalInterface
public interface HttpFetcher {
    String get(String url);
}
