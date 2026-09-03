package dsp.backend.service;

import dsp.backend.Entity.ETF;
import dsp.backend.Entity.Portfolio;
import dsp.backend.Entity.PortfolioId;
import dsp.backend.controller.PortfolioController;
import dsp.backend.repository.PortfolioRepository;
import dsp.backend.repository.UserRepository;
import dsp.backend.utils.CountryEnum;
import dsp.backend.repository.ETFRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ETFRepository etfRepository;

    @Transactional
    public Portfolio addPortfolio(String userId, String etfCode, Integer count) {
        try {
            etfCode = URLDecoder.decode(etfCode, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        Optional<ETF> etfOptional;
        if (etfCode.contains(" ")) {
            etfOptional = etfRepository.findByLongName(etfCode);
        } else {
            etfOptional = etfRepository.findBySymbol(etfCode);
            
        }
        etfCode = etfOptional.get().getSymbol();
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정보"));
        etfRepository.findBySymbol(etfCode).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정보"));

        PortfolioId portfolioId = new PortfolioId(userId, etfCode);
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);

        if (portfolio != null) {
            portfolio.setCount(portfolio.getCount() + count);
        } else {
            portfolio = new Portfolio();
            portfolio.setUserId(userId);
            portfolio.setSymbol(etfCode);
            portfolio.setCount(count);
        }

        return portfolioRepository.save(portfolio);
    }

    @Transactional
    public void deletePortfolio(String userId, String etfCode) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정보"));
        etfRepository.findBySymbol(etfCode).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정보"));

        PortfolioId portfolioId = new PortfolioId(userId, etfCode);
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정보"));

        portfolioRepository.delete(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PortfolioController.PortfolioResponse> getPortfolios(String userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 정보"));

        List<Portfolio> portfolios = portfolioRepository.findByUserIdWithEtf(userId);
        double totalMoney = portfolios.stream().mapToDouble(p -> {
            ETF etf = p.getEtf();
            if (etf.getCountry() == CountryEnum.USA) {
                return p.getCount() * etf.getCurrentPrice() * 1395;
            } else {
                return p.getCount() * etf.getCurrentPrice();
            }
        }).sum();

        return portfolios.stream().map(p -> {
            PortfolioController.PortfolioResponse response = new PortfolioController.PortfolioResponse();
            ETF etf = p.getEtf();
            if(etf.getCountry() == CountryEnum.USA){
                response.setMoney(p.getCount() * etf.getCurrentPrice() * 1395);
            } else {
                response.setMoney(p.getCount() * etf.getCurrentPrice());
            }

            response.setPercent((response.getMoney() / totalMoney) * 100);
            response.setEtfCode(p.getSymbol());
            response.setEtfName(etf.getLongName());
            response.setCount(p.getCount());
            response.setCountry(etf.getCountry());

            return response;
        }).collect(Collectors.toList());
    }
}
