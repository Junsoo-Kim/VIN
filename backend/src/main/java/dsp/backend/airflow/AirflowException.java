package dsp.backend.airflow;

public class AirflowException extends RuntimeException {
    public AirflowException(String message) {
        super(message);
    }

    public AirflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
