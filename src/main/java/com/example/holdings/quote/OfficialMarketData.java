package com.example.holdings.quote;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 證交所 STOCK_DAY（上市）與櫃買中心 tradingStock（上櫃）。
 * 兩邊都是「一次回一個月的日資料」，取最後一筆有收盤價的列。
 * 月初幾天當月可能還沒有資料，所以當月查不到時再查上個月。
 */
public class OfficialMarketData implements MarketDataSource {
    static final String TWSE_URL = "https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=%s&stockNo=%s&response=json";
    static final String TPEX_URL = "https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock?code=%s&date=%s&response=json";

    private final HttpFetcher fetcher;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public OfficialMarketData(HttpFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    @Override
    public Optional<Quote> latest(String code, Market market) {
        LocalDate month = LocalDate.now(clock).withDayOfMonth(1);
        Optional<Quote> q = fetchMonth(code, market, month);
        return q.isPresent() ? q : fetchMonth(code, market, month.minusMonths(1));
    }

    Optional<Quote> fetchMonth(String code, Market market, LocalDate month) {
        String body = market == Market.TWSE
            ? fetcher.get(TWSE_URL.formatted(month.format(DateTimeFormatter.BASIC_ISO_DATE), code))
            : fetcher.get(TPEX_URL.formatted(code, month.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))));
        return market == Market.TWSE ? parseTwse(code, body) : parseTpex(code, body);
    }

    /** 證交所：stat 不是 OK 代表該月查無資料（代號不存在或該月沒交易）。 */
    Optional<Quote> parseTwse(String code, String body) {
        JsonNode root = read(body);
        if (!"OK".equalsIgnoreCase(root.path("stat").asText())) return Optional.empty();
        // title 長這樣："115年11月 2330 台積電           各日成交資訊"
        String name = nameAfterCode(root.path("title").asText(), code);
        return lastClose(code, name, Market.TWSE, root.path("data"));
    }

    /** 櫃買中心：代號不存在時 stat 仍是 ok，只是 data 為空、name 為空字串。 */
    Optional<Quote> parseTpex(String code, String body) {
        JsonNode root = read(body);
        JsonNode table = root.path("tables").path(0);
        String name = root.path("name").asText("");
        if (name.isBlank()) {
            // subtitle 長這樣："6488 環球晶 115年11月"
            name = nameAfterCode(table.path("subtitle").asText(), code);
        }
        return lastClose(code, name, Market.TPEX, table.path("data"));
    }

    private Optional<Quote> lastClose(String code, String name, Market market, JsonNode rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            JsonNode row = rows.get(i);
            BigDecimal close = number(row.path(6).asText());
            if (close != null) {
                return Optional.of(new Quote(code, name, market, close, rocDate(row.path(0).asText())));
            }
        }
        return Optional.empty();
    }

    /** 民國年 "115/11/02" → 2026-11-02。 */
    static LocalDate rocDate(String s) {
        String[] p = s.trim().split("/");
        return LocalDate.of(Integer.parseInt(p[0]) + 1911, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    /** "1,234.50" → 1234.50；"--"、"" 這類沒成交的標記回 null。 */
    static BigDecimal number(String s) {
        String t = s.replace(",", "").trim();
        if (t.isEmpty() || !t.matches("-?\\d+(\\.\\d+)?")) return null;
        return new BigDecimal(t);
    }

    static String nameAfterCode(String text, String code) {
        String[] tokens = text.trim().split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].equals(code)) {
                String next = tokens[i + 1];
                return next.matches("\\d+年\\d+月") || next.equals("各日成交資訊") ? "" : next;
            }
        }
        return "";
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new QuoteSourceException("官方網站回傳的不是 JSON", e);
        }
    }

    /** 真正連網的 fetcher：逾時 5 秒；兩次連線至少間隔 300 毫秒，免得被證交所擋。 */
    public static HttpFetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new HttpFetcher() {
            private long lastCall;

            @Override
            public synchronized String get(String url) {
                long wait = lastCall + 300 - System.currentTimeMillis();
                try {
                    if (wait > 0) Thread.sleep(wait);
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "Mozilla/5.0 (holdings-demo)")
                        .header("Accept", "application/json")
                        .build();
                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() != 200) {
                        throw new QuoteSourceException("官方網站回應 HTTP " + res.statusCode());
                    }
                    return res.body();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new QuoteSourceException("查詢被中斷", e);
                } catch (QuoteSourceException e) {
                    throw e;
                } catch (Exception e) {
                    throw new QuoteSourceException("連不上官方網站：" + e.getMessage(), e);
                } finally {
                    lastCall = System.currentTimeMillis();
                }
            }
        };
    }
}
