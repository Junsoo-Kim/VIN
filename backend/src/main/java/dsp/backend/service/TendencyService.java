package dsp.backend.service;

import dsp.backend.Entity.Tendency;
import dsp.backend.Entity.User;
import dsp.backend.exception.TendencyNotFoundException;
import dsp.backend.repository.TendencyRepository;
import dsp.backend.utils.TendencyEnum;
import dsp.backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpClientErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TendencyService {

    private static final Logger logger = LoggerFactory.getLogger(TendencyService.class);

    @Autowired
    private TendencyRepository tendencyRepository;

    @Autowired
    private UserRepository userRepository; // UserRepository를 주입받습니다.

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.base-url}")
    private String apiBaseUrl;

    public void saveTendency(List<String> answers, List<String> reasons, String userId) {
        // User를 찾아 tendencyIndex를 확인합니다.
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getTendencyIndex() != null) {
            tendencyRepository.deleteById(user.getTendencyIndex());
        }

        Tendency tendency = new Tendency();
        tendency.setAnswer1(Integer.parseInt(answers.get(0)));
        tendency.setAnswer2(Integer.parseInt(answers.get(1)));
        tendency.setAnswer4(Integer.parseInt(answers.get(3)));
        tendency.setAnswer5(Integer.parseInt(answers.get(4)));
        tendency.setAnswer6(Integer.parseInt(answers.get(5)));
        tendency.setReason1(reasons.get(0));
        tendency.setReason2(reasons.get(1));
        tendency.setReason3(reasons.get(2));
        tendency.setReason4(reasons.get(3));
        tendency.setReason5(reasons.get(4));
        tendency.setReason6(reasons.get(5));

        try {
            Map<String, Object> result = evaluateTendencyWithDAG(tendency, userId);
            tendency.setScore(Double.parseDouble((String) result.get("final_score")));
            tendency.setTendency(TendencyEnum.valueOf((String) result.get("investment_trait")));
            tendency.setComment((String) result.get("comment"));
        } catch (IOException e) {
            logger.error("Error evaluating tendency with DAG", e);
        }

        tendencyRepository.save(tendency);

        // User의 tendencyIndex를 설정합니다.
        user.setTendencyIndex(tendency.getId());
        userRepository.save(user);
    }

    public Tendency getTendencyByUserId(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Long tendencyIndex = user.getTendencyIndex();
        if (tendencyIndex == null) {
            throw new TendencyNotFoundException("설문조사에 응답하지 않았습니다.");
        }

        return tendencyRepository.findById(tendencyIndex).orElseThrow(() -> new RuntimeException("Tendency not found"));
    }

    public Map<String, Object> evaluateTendencyWithDAG(Tendency tendency, String userId) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor("admin", "admin"));

        String dagRunUrl = "http://localhost:8081/api/v1/dags/llm_tendency_dag/dagRuns";
        String xcomUrlTemplate = "http://localhost:8081/api/v1/dags/llm_tendency_dag/dagRuns/{dag_run_id}/taskInstances/llm_tendency_dag/xcomEntries/return_value";

        logger.info("Triggering DAG for userId: {}", userId);

        // Trigger the DAG
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> conf = new HashMap<>();
        Map<String, Object> responses = new HashMap<>();
        responses.put("question1", Map.of("answer", tendency.getAnswer1(), "reason", tendency.getReason1()));
        responses.put("question2", Map.of("answer", tendency.getAnswer2(), "reason", tendency.getReason2()));
        responses.put("question3", Map.of("answer", tendency.getAnswer3(), "reason", tendency.getReason3()));
        responses.put("question4", Map.of("answer", tendency.getAnswer4(), "reason", tendency.getReason4()));
        responses.put("question5", Map.of("answer", tendency.getAnswer5(), "reason", tendency.getReason5()));
        responses.put("question6", Map.of("answer", tendency.getAnswer6(), "reason", tendency.getReason6()));
        conf.put("responses", responses);
        conf.put("api_key", apiKey); // 환경변수에서 읽어온 API 키 사용
        conf.put("api_base_url", apiBaseUrl); // GMS 프록시 등 커스텀 OpenAI 엔드포인트 사용
        requestBody.put("conf", conf);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(dagRunUrl, HttpMethod.POST, requestEntity, Map.class);
        } catch (Exception e) {
            logger.error("Failed to trigger DAG", e);
            throw new IOException("Failed to trigger DAG", e);
        }

        // Get the DAG run ID
        String dagRunId = (String) response.getBody().get("dag_run_id");
        logger.info("DAG triggered successfully, DAG run ID: {}", dagRunId);

        // Fetch XCom entries
        String xcomUrl = UriComponentsBuilder.fromUriString(xcomUrlTemplate)
                                             .buildAndExpand(dagRunId)
                                             .toUriString();
        ResponseEntity<String> xcomResponse;
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> xcomResult = null;

        while (xcomResult == null) {
            try {
                xcomResponse = restTemplate.exchange(xcomUrl, HttpMethod.GET, null, String.class);
                xcomResult = objectMapper.readValue(xcomResponse.getBody(), Map.class);
            } catch (HttpClientErrorException.NotFound e) {
                logger.info("XCom entry not found, retrying in 1 second...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for XCom entry", ie);
                }
            } catch (Exception e) {
                logger.error("Failed to fetch XCom entries", e);
                throw new IOException("Failed to fetch XCom entries", e);
            }
        }

        // Extract the value field from the XCom result
        String value = (String) xcomResult.get("value");
        value = value.replace("'", "\""); // Replace single quotes with double quotes
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(value, Map.class);
        } catch (Exception e) {
            logger.error("Failed to parse result value", e);
            throw new IOException("Failed to parse result value", e);
        }

        logger.info("Result fetched successfully for userId: {}", userId);
        logger.info("Result: {}", result);

        return result;
    }
}
