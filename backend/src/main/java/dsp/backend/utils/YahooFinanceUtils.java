package dsp.backend.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dsp.backend.Entity.ETF;
import dsp.backend.service.ETFService;

public class YahooFinanceUtils {
    private static final Logger logger = LoggerFactory.getLogger(ETFService.class);

    public static List<ETF> getDetailsFromCsv(String filePath) {
        List<ETF> etfList = new ArrayList<>();
        CountryEnum country = CountryEnum.USA;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header line
                }
                String[] values = line.split(",");
                ETF etf = new ETF();
                etf.setSymbol(values[0]);
                etf.setLongName(values[1]);
                etf.setCurrentPrice(Double.parseDouble(values[2]));
                etf.setSharesOutstanding((long) Double.parseDouble(values[3]));
                etf.setWeek52High(Double.parseDouble(values[4]));
                etf.setWeek52Low(Double.parseDouble(values[5]));
                etf.setBenchmark(values[6]);
                etf.setIpoDate(values[7]);
                etf.setExpenseRatio(values[8]);
                etf.setFundManager(values[9]);
                etf.setNavPrice(Double.parseDouble(values[10]));
                etf.setMonthChange(Double.parseDouble(values[11]));
                etf.setQuarterChange(Double.parseDouble(values[12]));
                etf.setYearChange(Double.parseDouble(values[13]));
                etf.setCountry(country);
                etfList.add(etf);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return etfList;
    }

    public static List<Map<String, String>> getAllYahooPrice(String symbol) throws IOException {
        boolean isFirstLine = true;

        List<Map<String, String>> priceData = new ArrayList<>();
        String filePath = "src\\main\\data\\ETF_USA\\" + symbol + ".csv";
        logger.info("Reading price data from file1: {}", filePath);
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            logger.info("Reading price data from file2: {}", filePath);

            for (String line : lines) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header line
                }
                logger.info("line: {}", line);
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    Map<String, String> data = new HashMap<>();
                    data.put("date", parts[0]);
                    data.put("price", parts[1]);
                    priceData.add(data);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading file: {}", filePath, e);
            throw e;
        }

        return priceData;
    }
}
