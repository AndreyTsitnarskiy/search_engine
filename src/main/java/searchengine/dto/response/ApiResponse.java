package searchengine.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse {
    private boolean result;
    private String messageError;

    public ApiResponse(boolean result, String messageError) {
        this.result = result;
        this.messageError = messageError;
    }

    public ApiResponse(boolean result) {
        this.result = result;
    }
}
