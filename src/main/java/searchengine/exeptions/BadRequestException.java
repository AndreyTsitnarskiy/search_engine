package searchengine.exeptions;

public class BadRequestException extends CustomException{
    public BadRequestException(String error) {
        super(error);
    }
}
