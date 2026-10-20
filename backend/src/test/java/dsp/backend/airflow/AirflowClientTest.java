package dsp.backend.airflow;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AirflowClientTest {

    private static final String BASE_URL = "http://airflow/api/v1";
    private static final String RUN_URL = BASE_URL + "/dags/test_dag/dagRuns/run1";

    private AirflowClient client(long timeoutSeconds, MockRestServiceServer[] server) {
        AirflowClient client = new AirflowClient(BASE_URL, "user", "pass", timeoutSeconds, 1);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server[0] = MockRestServiceServer.bindTo(restTemplate).build();
        server[0].expect(requestTo(BASE_URL + "/dags/test_dag/dagRuns")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"dag_run_id\":\"run1\"}", MediaType.APPLICATION_JSON));
        return client;
    }

    @Test
    void returnsParsedXcomWhenDagSucceeds() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        AirflowClient client = client(5, server);
        server[0].expect(requestTo(RUN_URL))
                .andRespond(withSuccess("{\"state\":\"running\"}", MediaType.APPLICATION_JSON));
        server[0].expect(requestTo(RUN_URL))
                .andRespond(withSuccess("{\"state\":\"success\"}", MediaType.APPLICATION_JSON));
        server[0].expect(requestTo(RUN_URL + "/taskInstances/task/xcomEntries/key"))
                .andRespond(withSuccess("{\"value\":\"{'a': ['X', 0.5]}\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.runAndGetXcom("test_dag", "task", "key", Map.of());

        assertThat(result).containsKey("a");
        server[0].verify();
    }

    @Test
    void failsFastWhenDagFails() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        AirflowClient client = client(5, server);
        server[0].expect(requestTo(RUN_URL))
                .andRespond(withSuccess("{\"state\":\"failed\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.runAndGetXcom("test_dag", "task", "key", Map.of()))
                .isInstanceOf(AirflowException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void givesUpAfterTimeout() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        AirflowClient client = client(1, server);
        server[0].expect(manyTimes(), requestTo(RUN_URL))
                .andRespond(withSuccess("{\"state\":\"running\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.runAndGetXcom("test_dag", "task", "key", Map.of()))
                .isInstanceOf(AirflowException.class)
                .hasMessageContaining("시간 초과");
    }
}
