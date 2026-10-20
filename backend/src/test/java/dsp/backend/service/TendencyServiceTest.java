package dsp.backend.service;

import dsp.backend.Entity.Tendency;
import dsp.backend.Entity.User;
import dsp.backend.airflow.AirflowClient;
import dsp.backend.airflow.AirflowException;
import dsp.backend.repository.TendencyRepository;
import dsp.backend.repository.UserRepository;
import dsp.backend.utils.TendencyEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TendencyServiceTest {

    private static final List<String> ANSWERS = List.of("50", "25", "-25", "0", "-50", "25");
    private static final List<String> REASONS = List.of("r1", "r2", "r3", "r4", "r5", "r6");

    private TendencyRepository tendencyRepository;
    private UserRepository userRepository;
    private AirflowClient airflowClient;
    private TendencyService tendencyService;
    private User user;

    @BeforeEach
    void setUp() {
        tendencyRepository = mock(TendencyRepository.class);
        userRepository = mock(UserRepository.class);
        airflowClient = mock(AirflowClient.class);
        tendencyService = new TendencyService();
        ReflectionTestUtils.setField(tendencyService, "tendencyRepository", tendencyRepository);
        ReflectionTestUtils.setField(tendencyService, "userRepository", userRepository);
        ReflectionTestUtils.setField(tendencyService, "airflowClient", airflowClient);

        user = new User();
        user.setUserId("alice");
        user.setTendencyIndex(1L);
        when(userRepository.findById("alice")).thenReturn(Optional.of(user));
        when(tendencyRepository.save(any(Tendency.class))).thenAnswer(invocation -> {
            Tendency tendency = invocation.getArgument(0);
            tendency.setId(2L);
            return tendency;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveTendency_sendsEveryAnswerAndReplacesPreviousResult() {
        when(airflowClient.runAndGetXcom(eq("llm_tendency_dag"), any(), any(), anyMap()))
                .thenReturn(Map.of("final_score", "12.5", "investment_trait", "중립적", "comment", "ok"));

        tendencyService.saveTendency(ANSWERS, REASONS, "alice");

        ArgumentCaptor<Map<String, Object>> conf = ArgumentCaptor.forClass(Map.class);
        verify(airflowClient).runAndGetXcom(eq("llm_tendency_dag"), any(), any(), conf.capture());
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) conf.getValue().get("responses");
        for (int i = 0; i < ANSWERS.size(); i++) {
            assertThat(responses.get("question" + (i + 1)).get("answer")).isEqualTo(Integer.parseInt(ANSWERS.get(i)));
        }
        assertThat(conf.getValue()).doesNotContainKey("api_key");

        ArgumentCaptor<Tendency> saved = ArgumentCaptor.forClass(Tendency.class);
        verify(tendencyRepository).save(saved.capture());
        assertThat(saved.getValue().getScore()).isEqualTo(12.5);
        assertThat(saved.getValue().getTendency()).isEqualTo(TendencyEnum.중립적);
        assertThat(user.getTendencyIndex()).isEqualTo(2L);
        verify(tendencyRepository).deleteById(1L);
    }

    @Test
    void saveTendency_keepsPreviousResultWhenEvaluationFails() {
        when(airflowClient.runAndGetXcom(any(), any(), any(), anyMap())).thenThrow(new AirflowException("failed"));

        assertThatThrownBy(() -> tendencyService.saveTendency(ANSWERS, REASONS, "alice"))
                .isInstanceOf(AirflowException.class);

        verify(tendencyRepository, never()).save(any());
        verify(tendencyRepository, never()).deleteById(anyLong());
        assertThat(user.getTendencyIndex()).isEqualTo(1L);
    }
}
