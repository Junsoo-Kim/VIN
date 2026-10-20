package dsp.backend.airflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;

@Component
public class AirflowClient {

    private static final Logger logger = LoggerFactory.getLogger(AirflowClient.class);
    private static final String BASE_URL = "http://localhost:8081/api/v1";
    private static final Set<String> FAILED_STATES = Set.of("failed", "upstream_failed");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long timeoutMillis;
    private final long pollIntervalMillis;

    public AirflowClient(@Value("${airflow.timeout-seconds:60}") long timeoutSeconds,
                         @Value("${airflow.poll-interval-millis:1000}") long pollIntervalMillis) {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor("admin", "admin"));
        this.timeoutMillis = timeoutSeconds * 1000;
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public Map<String, Object> runAndGetXcom(String dagId, String taskId, String xcomKey, Map<String, Object> conf) {
        String dagRunId = trigger(dagId, conf);
        String dagRunUrl = UriComponentsBuilder.fromUriString(BASE_URL + "/dags/{dagId}/dagRuns/{dagRunId}")
                .buildAndExpand(dagId, dagRunId).toUriString();
        String xcomUrl = UriComponentsBuilder
                .fromUriString(BASE_URL + "/dags/{dagId}/dagRuns/{dagRunId}/taskInstances/{taskId}/xcomEntries/{key}")
                .buildAndExpand(dagId, dagRunId, taskId, xcomKey).toUriString();

        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            String state = fetchState(dagRunUrl);
            if (FAILED_STATES.contains(state)) {
                throw new AirflowException("DAG 실행 실패: dagId=" + dagId + ", dagRunId=" + dagRunId + ", state=" + state);
            }
            if ("success".equals(state)) {
                return fetchXcom(xcomUrl, dagId, dagRunId);
            }
            sleep();
        }
        throw new AirflowException("DAG 실행 시간 초과(" + timeoutMillis / 1000 + "s): dagId=" + dagId + ", dagRunId=" + dagRunId);
    }

    private String trigger(String dagId, Map<String, Object> conf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("conf", conf), headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    BASE_URL + "/dags/" + dagId + "/dagRuns", HttpMethod.POST, request, Map.class);
            String dagRunId = (String) response.getBody().get("dag_run_id");
            logger.info("DAG triggered: dagId={}, dagRunId={}", dagId, dagRunId);
            return dagRunId;
        } catch (Exception e) {
            throw new AirflowException("DAG 트리거 실패: dagId=" + dagId, e);
        }
    }

    private String fetchState(String dagRunUrl) {
        try {
            Map<?, ?> dagRun = restTemplate.getForObject(dagRunUrl, Map.class);
            return dagRun == null ? null : (String) dagRun.get("state");
        } catch (Exception e) {
            throw new AirflowException("DAG 상태 조회 실패: " + dagRunUrl, e);
        }
    }

    private Map<String, Object> fetchXcom(String xcomUrl, String dagId, String dagRunId) {
        try {
            Map<?, ?> xcom = restTemplate.getForObject(xcomUrl, Map.class);
            String value = ((String) xcom.get("value")).replace("'", "\"");
            return objectMapper.readValue(value, Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AirflowException("DAG는 성공했지만 XCom 결과가 없습니다: dagId=" + dagId + ", dagRunId=" + dagRunId, e);
        } catch (Exception e) {
            throw new AirflowException("XCom 결과 파싱 실패: dagId=" + dagId + ", dagRunId=" + dagRunId, e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(pollIntervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AirflowException("DAG 결과 대기 중 인터럽트", e);
        }
    }
}
