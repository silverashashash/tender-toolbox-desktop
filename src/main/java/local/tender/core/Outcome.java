package local.tender.core;
import java.util.List;
public sealed interface Outcome<T> permits Outcome.Success, Outcome.Failure {
 record Success<T>(T value,List<Problem> warnings) implements Outcome<T>{public Success{warnings=List.copyOf(warnings);}}
 record Failure<T>(List<Problem> problems) implements Outcome<T>{public Failure{problems=List.copyOf(problems);if(problems.isEmpty())throw new IllegalArgumentException("Empty failure");}}
 static <T> Outcome<T> ok(T value){return new Success<>(value,List.of());}
 static <T> Outcome<T> fail(String code,String message){return new Failure<>(List.of(new Problem(code,message,null)));}
}
