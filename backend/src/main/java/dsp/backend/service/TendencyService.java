package dsp.backend.service;

import dsp.backend.Entity.Tendency;
import dsp.backend.airflow.AirflowClient;
import dsp.backend.Entity.User;
import dsp.backend.exception.TendencyNotFoundException;
import dsp.backend.repository.TendencyRepository;
import dsp.backend.utils.TendencyEnum;
import dsp.backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

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

    @Autowired
    private AirflowClient airflowClient;

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.base-url}")
    private String apiBaseUrl;

    public void saveTendency(List<String> answers, List<String> reasons, String userId) {
        // User를 찾아 tendencyIndex를 확인합니다.
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        Tendency tendency = new Tendency();
        tendency.setAnswer1(Integer.parseInt(answers.get(0)));
        tendency.setAnswer2(Integer.parseInt(answers.get(1)));
        tendency.setAnswer3(Integer.parseInt(answers.get(2)));
        tendency.setAnswer4(Integer.parseInt(answers.get(3)));
        tendency.setAnswer5(Integer.parseInt(answers.get(4)));
        tendency.setAnswer6(Integer.parseInt(answers.get(5)));
        tendency.setReason1(reasons.get(0));
        tendency.setReason2(reasons.get(1));
        tendency.setReason3(reasons.get(2));
        tendency.setReason4(reasons.get(3));
        tendency.setReason5(reasons.get(4));
        tendency.setReason6(reasons.get(5));

        Map<String, Object> result = evaluateTendencyWithDAG(tendency, userId);
        tendency.setScore(Double.parseDouble((String) result.get("final_score")));
        tendency.setTendency(TendencyEnum.valueOf((String) result.get("investment_trait")));
        tendency.setComment((String) result.get("comment"));

        tendencyRepository.save(tendency);

        Long previousTendencyIndex = user.getTendencyIndex();
        // User의 tendencyIndex를 설정합니다.
        user.setTendencyIndex(tendency.getId());
        userRepository.save(user);
        if (previousTendencyIndex != null) {
            tendencyRepository.deleteById(previousTendencyIndex);
        }
    }

    public Tendency getTendencyByUserId(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Long tendencyIndex = user.getTendencyIndex();
        if (tendencyIndex == null) {
            throw new TendencyNotFoundException("설문조사에 응답하지 않았습니다.");
        }

        return tendencyRepository.findById(tendencyIndex).orElseThrow(() -> new RuntimeException("Tendency not found"));
    }

    public Map<String, Object> evaluateTendencyWithDAG(Tendency tendency, String userId) {
        Map<String, Object> responses = new HashMap<>();
        responses.put("question1", Map.of("answer", tendency.getAnswer1(), "reason", tendency.getReason1()));
        responses.put("question2", Map.of("answer", tendency.getAnswer2(), "reason", tendency.getReason2()));
        responses.put("question3", Map.of("answer", tendency.getAnswer3(), "reason", tendency.getReason3()));
        responses.put("question4", Map.of("answer", tendency.getAnswer4(), "reason", tendency.getReason4()));
        responses.put("question5", Map.of("answer", tendency.getAnswer5(), "reason", tendency.getReason5()));
        responses.put("question6", Map.of("answer", tendency.getAnswer6(), "reason", tendency.getReason6()));
        Map<String, Object> conf = new HashMap<>();
        conf.put("responses", responses);
        conf.put("api_key", apiKey); // 환경변수에서 읽어온 API 키 사용
        conf.put("api_base_url", apiBaseUrl); // GMS 프록시 등 커스텀 OpenAI 엔드포인트 사용

        logger.info("Evaluating tendency for userId: {}", userId);
        Map<String, Object> result = airflowClient.runAndGetXcom("llm_tendency_dag", "llm_tendency_dag", "return_value", conf);
        logger.info("Result fetched successfully for userId: {}", userId);
        return result;
    }
}
