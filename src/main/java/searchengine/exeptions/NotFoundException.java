package searchengine.exeptions;

public class NotFoundException extends CustomException {
    public NotFoundException(String error){
        super(error);
    }
}
