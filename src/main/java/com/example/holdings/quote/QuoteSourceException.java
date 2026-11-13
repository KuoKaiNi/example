package com.example.holdings.quote;

/** 官方網站連不上、逾時或回傳看不懂的內容。跟「查無此股」不同。 */
public class QuoteSourceException extends RuntimeException {
    public QuoteSourceException(String message, Throwable cause) { super(message, cause); }
    public QuoteSourceException(String message) { super(message); }
}
