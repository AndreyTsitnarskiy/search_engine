package searchengine.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ApiResponse {
    private boolean result;
    private String messageError;

    public static ApiResponse success(String message) {
        return new ApiResponse(true, message);
    }

    public static ApiResponse failure(String message) {
        return new ApiResponse(false, message);
    }
}
