package dsp.backend.service;

import dsp.backend.airflow.AirflowClient;
import dsp.backend.utils.CountryEnum;
import dsp.backend.utils.NaverFinanceUtils;
import dsp.backend.utils.PriceConverter;
import dsp.backend.utils.YahooFinanceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dsp.backend.repository.ETFRepository;
import dsp.backend.repository.TendencyRepository;
import dsp.backend.repository.UserRepository;
import dsp.backend.Entity.ETF;
import dsp.backend.Entity.Tendency;
import dsp.backend.Entity.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Set;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;



@Service
public class ETFService {

    private static final Logger logger = LoggerFactory.getLogger(ETFService.class);

    @Autowired
    private ETFRepository etfRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TendencyRepository tendencyRepository;

    @Autowired
    private AirflowClient airflowClient;

    @Autowired
    private PriceConverter priceConverter;

    private List<Map<String, String>> toKrw(CountryEnum country, List<Map<String, String>> priceData) {
        return priceData.stream().map(data -> {
            Map<String, String> converted = new HashMap<>(data);
            converted.put("price", String.valueOf(priceConverter.toKrw(country, Double.parseDouble(data.get("price")))));
            return converted;
        }).collect(Collectors.toList());
    }

    public Optional<Map<String, Object>> getETFInfo(String symbol) {
        logger.info("Fetching ETF info for symbol: {}", symbol);
        try {
            symbol = URLDecoder.decode(symbol, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error("Error decoding symbol: {}", symbol, e);
        }
        Optional<ETF> etfOptional;
        if (symbol.contains(" ")) {
            etfOptional = etfRepository.findByLongName(symbol);
        } else {
            etfOptional = etfRepository.findBySymbol(symbol);
        }
        if (etfOptional.isPresent()) {
            ETF etf = etfOptional.get();
            Map<String, Object> etfInfo = new HashMap<>();
            etfInfo.put("symbol", etf.getSymbol());
            etfInfo.put("longName", etf.getLongName());
            CountryEnum country = etf.getCountry();
            double currentPrice = priceConverter.toKrw(country, etf.getCurrentPrice());
            double week52High = Math.round(priceConverter.toKrw(country, etf.getWeek52High()));
            double week52Low = Math.round(priceConverter.toKrw(country, etf.getWeek52Low()));
            double navPrice = Math.round(priceConverter.toKrw(country, etf.getNavPrice()));
            etfInfo.put("currentPrice", currentPrice);
            etfInfo.put("week52High", week52High);
            etfInfo.put("week52Low", week52Low);
            etfInfo.put("sharesOutstanding", etf.getSharesOutstanding());
            etfInfo.put("benchmark", etf.getBenchmark());
            etfInfo.put("ipoDate", etf.getIpoDate());
            etfInfo.put("expenseRatio", etf.getExpenseRatio());
            etfInfo.put("fundManager", etf.getFundManager());
            etfInfo.put("navPrice", navPrice);
            etfInfo.put("monthChange", etf.getMonthChange());
            etfInfo.put("quarterChange", etf.getQuarterChange());
            etfInfo.put("yearChange", etf.getYearChange());
            etfInfo.put("country", etf.getCountry().name());
            return Optional.of(etfInfo);
        } else {
            return Optional.empty();
        }
    }

    @Transactional
    public void saveNaverETFInfo(String symbol) throws IOException {
        logger.info("Fetching ETF info for symbol: {}", symbol);
        Map<String, Object> etfInfo = NaverFinanceUtils.getETFInfo(symbol);
        CountryEnum country = CountryEnum.KOREA;
        ETF etf = new ETF();
        etf.setSymbol((String) etfInfo.get("symbol"));
        etf.setLongName((String) etfInfo.get("longName"));
        etf.setCurrentPrice((Double) etfInfo.get("currentPrice"));
        etf.setSharesOutstanding((Long) etfInfo.get("sharesOutstanding"));
        etf.setWeek52High((Double) etfInfo.get("week52High"));
        etf.setWeek52Low((Double) etfInfo.get("week52Low"));
        etf.setBenchmark((String) etfInfo.get("benchmark"));
        etf.setIpoDate((String) etfInfo.get("ipoDate"));
        etf.setExpenseRatio(String.valueOf(etfInfo.get("expenseRatio")));
        etf.setFundManager((String) etfInfo.get("fundManager"));
        etf.setNavPrice((Double) etfInfo.get("navPrice"));
        etf.setMonthChange((Double) etfInfo.get("monthChange"));
        etf.setQuarterChange((Double) etfInfo.get("quarterChange"));
        etf.setYearChange((Double) etfInfo.get("yearChange"));
        etf.setCountry(country);
        etfRepository.save(etf);
        logger.info("Saved ETF info for symbol: {}", symbol);
    }

    public List<Map<String, String>> getAllPrice(String symbol) throws IOException {
        logger.info("Fetching ETF info for symbol: {}", symbol);
        try {
            symbol = URLDecoder.decode(symbol, "UTF-8");
            logger.info("decode ETF symbol: {}", symbol);
        } catch (UnsupportedEncodingException e) {
            logger.error("Error decoding symbol: {}", symbol, e);
        }
        Optional<ETF> etfOptional;
        if (symbol.contains(" ")) {
            etfOptional = etfRepository.findByLongName(symbol);
        } else {
            etfOptional = etfRepository.findBySymbol(symbol);
        }
        
        if (etfOptional.isPresent()) {
            ETF etf = etfOptional.get();
            symbol = etf.getSymbol();
            if (etf.getCountry() == CountryEnum.KOREA) {
                return NaverFinanceUtils.getAllNaverPrice(symbol);
            } 
            else if(etf.getCountry() == CountryEnum.USA) {
                return toKrw(etf.getCountry(), YahooFinanceUtils.getAllYahooPrice(symbol));
            } else {
                logger.error("Country not supported: {}", etf.getCountry());
                return List.of();
            }
        } else {
            logger.info("ETF not found for symbol: {}", symbol);
            return List.of();
        }
    }

    // 첫 수행 시 DB에 저장된 ETF 정보가 �����는 경우에만 주석 풀고 수행
    // @PostConstruct
    public void saveYahooETFs() {
        List<ETF> etfList = YahooFinanceUtils.getDetailsFromCsv("src\\main\\data\\usa_etfs_info.csv");
        for (ETF etf : etfList) {
            if (!etfRepository.existsBySymbol(etf.getSymbol())) {
                etfRepository.save(etf);
                logger.info("Saved ETF: {}", etf.getSymbol());
            } else {
                logger.info("ETF already exists: {}", etf.getSymbol());
            }
        }
    }

    // 첫 수행 시 DB에 저장된 ETF 정보가 없는 경우에만 주석 풀고 수행
    // @PostConstruct
    public void saveNaverETFs() {
        logger.info("Starting saveFilteredETFs method");

        try {
            // 1. 디렉토리 확인
            File folderKOR = new File("src\\main\\data\\ETF_KOR");
            if (!folderKOR.exists() || !folderKOR.isDirectory()) {
                logger.error("Directory does not exist or is not a directory: {}", folderKOR.getAbsolutePath());
                return;
            }

            // 2. CSV 파일 목록 가져오기 및 정렬
            File[] listOfFilesKOR = folderKOR.listFiles((dir, name) -> name.endsWith(".csv"));
            if (listOfFilesKOR == null) {
                logger.error("No files found in directory: {}", folderKOR.getAbsolutePath());
                return;
            }
            Arrays.sort(listOfFilesKOR, (f1, f2) -> f1.getName().compareTo(f2.getName()));

            // 3. 파일 이름에서 쿼리 추출
            Set<String> queriesKOR = Arrays.stream(listOfFilesKOR)
                                           .map(File::getName)
                                           .collect(Collectors.toSet());

            // 4. 네이버 증권에서 ETF 정보 가져오고 저장하기
            for (String query : queriesKOR){
                Map<String, String> etfTag = NaverFinanceUtils.searchETFs(query);
                Map<String, Object> etfInfo = NaverFinanceUtils.getETFInfo(etfTag.get("symbol"));
                ETF etf = new ETF();
                etf.setSymbol((String) etfInfo.get("symbol"));
                etf.setLongName((String) etfInfo.get("longName"));
                etf.setCurrentPrice((Double) etfInfo.get("currentPrice"));
                etf.setSharesOutstanding((Long) etfInfo.get("sharesOutstanding"));
                etf.setWeek52High((Double) etfInfo.get("week52High"));
                etf.setWeek52Low((Double) etfInfo.get("week52Low"));
                etf.setBenchmark((String) etfInfo.get("benchmark"));
                etf.setIpoDate((String) etfInfo.get("ipoDate"));
                etf.setExpenseRatio(String.valueOf(etfInfo.get("expenseRatio")));
                etf.setFundManager((String) etfInfo.get("fundManager"));
                etf.setNavPrice((Double) etfInfo.get("navPrice"));
                etf.setMonthChange((Double) etfInfo.get("monthChange"));
                etf.setQuarterChange((Double) etfInfo.get("quarterChange"));
                etf.setYearChange((Double) etfInfo.get("yearChange"));
                etf.setCountry(CountryEnum.KOREA);
                etfRepository.save(etf);
                logger.info("Saved ETF info for symbol: {}", etf.getSymbol());
            }  
        } catch(Exception e){
            logger.error("Error occurred while saving filtered ETFs", e);
        }

        logger.info("Completed saveFilteredETFs method");
    }

    public List<Map<String, Object>> getsimpleETFPrices(List<String> symbols) throws IOException {
        List<ETF> etfs = etfRepository.findBySymbolIn(symbols);
        List<Map<String, Object>> etfDetails = new ArrayList<>();
        for (ETF etf : etfs) {
            logger.info("Processing ETF: {}", etf);
            Map<String, Object> etfDetail = new HashMap<>();
            etfDetail.put("symbol", etf.getSymbol());
            etfDetail.put("etfName", etf.getLongName());
            etfDetail.put("etfPrice", (double) Math.round(priceConverter.toKrw(etf.getCountry(), etf.getCurrentPrice())));
            etfDetail.put("Country", etf.getCountry().name());
            etfDetail.put("fundmanager", etf.getFundManager());

            List<Map<String, String>> priceData;
            if (etf.getCountry() == CountryEnum.KOREA) {
                priceData = NaverFinanceUtils.getAllNaverPrice(etf.getSymbol());
                etfDetail.put("chartPrice", priceData.stream()
                    .skip(Math.max(0, priceData.size() - 36))
                    .map(data -> data.get("price"))
                    .collect(Collectors.toList()));
            } else if (etf.getCountry() == CountryEnum.USA) {
                priceData = toKrw(etf.getCountry(), YahooFinanceUtils.getAllYahooPrice(etf.getSymbol()));
                etfDetail.put("chartPrice", priceData.stream()
                    .skip(Math.max(0, priceData.size() - 11))
                    .map(data -> data.get("price"))
                    .collect(Collectors.toList()));
            }

            etfDetails.add(etfDetail);
        }

        return etfDetails;
    }

    public List<Map<String, Object>> getETFDetails(List<String> symbols) throws IOException {
        logger.info("symbols: {}", symbols);
        List<ETF> etfs = etfRepository.findBySymbolIn(symbols);
        logger.info("Queried symbols: {}", symbols);
        logger.info("Found ETFs: {}", etfs);

        List<Map<String, Object>> etfDetails = new ArrayList<>();
        for (ETF etf : etfs) {
            logger.info("Processing ETF: {}", etf);
            Map<String, Object> etfDetail = new HashMap<>();
            etfDetail.put("symbol", etf.getSymbol());
            etfDetail.put("etfName", etf.getLongName());
            etfDetail.put("etfPrice", (double) Math.round(priceConverter.toKrw(etf.getCountry(), etf.getCurrentPrice())));
            etfDetail.put("Country", etf.getCountry().name());
            etfDetail.put("fundmanager", etf.getFundManager());

            List<Map<String, String>> priceData;
            if (etf.getCountry() == CountryEnum.KOREA) {
                priceData = NaverFinanceUtils.getAllNaverPrice(etf.getSymbol());
                etfDetail.put("chartPrice", priceData.stream()
                    .skip(Math.max(0, priceData.size() - 36))
                    .map(data -> data.get("price"))
                    .collect(Collectors.toList()));
            } else if (etf.getCountry() == CountryEnum.USA) {
                priceData = toKrw(etf.getCountry(), YahooFinanceUtils.getAllYahooPrice(etf.getSymbol()));
                etfDetail.put("chartPrice", priceData.stream()
                    .skip(Math.max(0, priceData.size() - 11))
                    .map(data -> data.get("price"))
                    .collect(Collectors.toList()));
            }

            etfDetails.add(etfDetail);
        }

        return etfDetails;
    }

    public List<Map<String, Object>> getAllETFs() {
        List<ETF> etfs = etfRepository.findAll();
        return etfs.stream()
                   .map(etf -> {
                       Map<String, Object> etfDetails = new HashMap<>();
                       etfDetails.put("symbol", etf.getSymbol());
                       etfDetails.put("longName", etf.getLongName());
                       etfDetails.put("country", etf.getCountry().name());
                       etfDetails.put("currentPrice", priceConverter.toKrw(etf.getCountry(), etf.getCurrentPrice()));
                       return etfDetails;
                   })
                   .collect(Collectors.toList());
    }

    public Map<String, Object> recommendETF(String symbol, String userId) {
        User user = userRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자"));
        ETF etf = etfRepository.findBySymbol(symbol).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 ETF"));

        String corrKey = etf.getCountry() == CountryEnum.KOREA ? etf.getLongName() : etf.getSymbol();

        Optional<Tendency> tendency = Optional.ofNullable(user.getTendencyIndex()).flatMap(tendencyRepository::findById);
        logger.info("Requesting recommendation: symbol={}, tendencyFound={}", corrKey, tendency.isPresent());

        if (tendency.isEmpty()) {
            return airflowClient.runAndGetXcom("recommend_dag", "recommend_stocks", "recommendation_result",
                    Map.of("input_stock", corrKey));
        }
        return airflowClient.runAndGetXcom("recommend_with_tendency_dag", "recommend_tendency", "recommendation_result",
                Map.of("input_stock", corrKey, "final_score", tendency.get().getScore()));
    }
}
