package mil.tron.commonapi;

public class MockToken {
    // JWT that has embeded email of test@test.com
    public static final String token = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJlbWFpbCI6InRlc3RAdGVzdC5jb20ifQ.04BSkxtfqwws2v893h2CDJHFtc7bqn0CGmdDIGI80TA";

    // MD5 of that email address string
    public static final String EMAIL_MD5 = "b642b4217b34b1e8d3bd915fc65c4452";
}
