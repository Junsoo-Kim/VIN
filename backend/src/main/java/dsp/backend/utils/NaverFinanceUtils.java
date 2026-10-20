package dsp.backend.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dsp.backend.service.ETFService;

import org.jsoup.Connection;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NaverFinanceUtils {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final Logger logger = LoggerFactory.getLogger(ETFService.class);

    public static Map<String, String> searchETFs(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, Charset.forName("EUC-KR").toString());
        String url = "https://finance.naver.com/search/search.naver?query=" + encodedQuery + "&page=1";

        // Jsoup을 사용하여 리다이렉션을 자동으로 따라가도록 설정
        Connection connection = Jsoup.connect(url).userAgent(USER_AGENT).followRedirects(true);
        Document doc = connection.get();


        // 일반 검색 결과 처리
        Element aTag = doc.selectFirst("a[href*='/item/main.naver?code=']");
        if (aTag != null) {
            String href = aTag.attr("href");
            String code = href.split("=")[1];
            String name = aTag.text();
            Map<String, String> etf = new HashMap<>();
            etf.put("code", code);
            etf.put("name", name);
            return etf;
        }

        // <SCRIPT> 태그에서 리다이렉션 URL 추출
        // 상품 검색 결과가 1개인 경우 자동으로 리다이렉션되는 경우가 있음
        String redirectUrl = extractRedirectUrl(doc);
        if (redirectUrl != null) {

            // 리다이렉션된 페이지로 이동하여 정보 추출
            doc = Jsoup.connect("https://finance.naver.com" + redirectUrl).userAgent(USER_AGENT).get();
            String code = redirectUrl.split("=")[1];
            String name = getText(doc, ".wrap_company h2 a");
            Map<String, String> etf = new HashMap<>();
            etf.put("code", code);
            etf.put("name", name);
            return etf;
        }


        return null;
    }

    private static String extractRedirectUrl(Document doc) {
        Element scriptTag = doc.selectFirst("script");
        if (scriptTag != null) {
            String scriptContent = scriptTag.html();
            if (scriptContent.contains("parent.location.href")) {
                return scriptContent.split("'")[1];
            }
        }
        return null;
    }

    public static Map<String, Object> getETFInfo(String symbol) throws IOException {
        String url = "https://finance.naver.com/item/main.naver?code=" + symbol;
        Document doc = Jsoup.connect(url).get();

        Map<String, Object> etfInfo = new HashMap<>();
        etfInfo.put("symbol", symbol);
        etfInfo.put("longName", getText(doc, ".wrap_company h2 a"));
        etfInfo.put("currentPrice", parseDouble(getText(doc, ".no_today .blind")));
        etfInfo.put("sharesOutstanding", parseLong(getText(doc, "table[summary='시가총액 정보'] tr:nth-child(2) em")));
        etfInfo.put("week52High", parseDouble(getText(doc, "table[summary='시가총액 정보'] tr:nth-child(3) em:nth-child(1)")));
        etfInfo.put("week52Low", parseDouble(getText(doc, "table[summary='시가총액 정보'] tr:nth-child(3) em:nth-child(2)")));
        etfInfo.put("benchmark", getText(doc, "table[summary='기초지수 정보'] tr:nth-child(1) span"));
        etfInfo.put("ipoDate", getText(doc, "table[summary='기초지수 정보'] tr:nth-child(3) td"));
        etfInfo.put("expenseRatio", parseDouble(getText(doc, "table[summary='펀드보수 정보'] tr:nth-child(1) em")));
        etfInfo.put("fundManager", getText(doc, "table[summary='펀드보수 정보'] tr:nth-child(2) span"));
        etfInfo.put("navPrice", parseDouble(getText(doc, "#on_board_last_nav em strong")));
        etfInfo.put("monthChange", parseDouble(getText(doc, "table[summary='1개월 수익률 정보'] tr:nth-child(1) em")));
        etfInfo.put("quarterChange", parseDouble(getText(doc, "table[summary='1개월 수익률 정보'] tr:nth-child(2) em")));
        etfInfo.put("yearChange", parseDouble(getText(doc, "table[summary='1개월 수익률 정보'] tr:nth-child(3) em")));

        return etfInfo;
    }

    public static List<Map<String, String>> getAllNaverPrice(Path dataDir, String symbol) throws IOException {
        Map<String, Object> etfInfo = getETFInfo(symbol);
        String longName = (String) etfInfo.get("longName");
        boolean isFirstLine = true;

        List<Map<String, String>> priceData = new ArrayList<>();
        Path filePath = dataDir.resolve("ETF_KOR").resolve(longName + ".csv");
        List<String> lines = Files.readAllLines(filePath);
        logger.info("Reading price data from file: {}", filePath);
    
        for (String line : lines) {
            if (isFirstLine) {
                isFirstLine = false;
                continue; // Skip header line
            }
            logger.info("line: {}", line);
            String[] parts = line.split(",");
            if (parts.length == 4) {
                String[] slicedParts = Arrays.copyOfRange(parts, 1, 3);
                Map<String, String> data = new HashMap<>();
                data.put("date", slicedParts[0]);
                data.put("price", slicedParts[1]);
                priceData.add(data);
            }
        }
        
        return priceData;
    }

    private static String getText(Document doc, String cssQuery) {
        Element element = doc.selectFirst(cssQuery);
        return element != null ? element.text() : "";
    }

    private static Double parseDouble(String text) {
        try {
            return Double.parseDouble(text.replaceAll(",", "").replaceAll("%", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static Long parseLong(String text) {
        try {
            return Long.parseLong(text.replaceAll(",", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}