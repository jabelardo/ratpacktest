package jags.ratpacktest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.assertj.core.api.StrictAssertions;
import org.h2.util.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import ratpack.func.Action;
import ratpack.http.MediaType;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;
import ratpack.test.MainClassApplicationUnderTest;
import ratpack.test.http.TestHttpClient;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jose abelardo gutierrez on 7/26/15.
 */
public class AppTest {
  
  private static final ObjectMapper mapper = new ObjectMapper();
  
  private static MainClassApplicationUnderTest aut;

  private static Configuration freemarkerCfg;
  
  private TestHttpClient client;

  @BeforeClass
  public static void beforeClass() throws IOException {
    aut = new MainClassApplicationUnderTest(App.class);
    freemarkerCfg = App.initFreemarker();
  }
  
  @AfterClass
  public static void afterClass() {
    aut.stop();
  }

  @Before
  public void before() {
    client = TestHttpClient.testHttpClient(aut);
  }

  @Test
  public void getHelloTest() {
    ReceivedResponse response = client.get("/hello");
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.getBody().getText()).isEqualTo("Hello, Ratpack");
  }

  @Test
  public void createBookmarkTest() throws Exception {
    ReceivedResponse response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    int lastSize = bookmarks.length;

    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);
    assertThat(response.getBody().getText()).matches("/api/bookmarks/\\d+");

    response = client.get("/api/bookmarks");
    bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    assertThat(bookmarks.length).isEqualTo(lastSize + 1);
  }

  private Action<RequestSpec> jsonRequestBody(Bookmark bookmark) throws JsonProcessingException {
    return requestSpec -> requestSpec.getBody()
                .type(MediaType.APPLICATION_JSON)
                .text(mapper.writeValueAsString(bookmark));
  }

  @Test
  public void getBookmarkTest() throws Exception {
    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    response = client.get("/api/bookmarks/" + id);
    Bookmark retrieved = mapper.readValue(response.getBody().getText(), Bookmark.class);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(retrieved.getId()).isEqualTo(id);
    assertThat(retrieved.getTitle()).isEqualTo("Test");
    assertThat(retrieved.getUrl()).isEqualTo("http://www.test.com");
  }

  @Test
  public void updateBookmarkTest() throws Exception {
    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    bookmark.setTitle("Success");
    response = client.requestSpec(jsonRequestBody(bookmark)).put("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);

    response = client.get("/api/bookmarks/" + id);
    Bookmark retrieved = mapper.readValue(response.getBody().getText(), Bookmark.class);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(retrieved.getId()).isEqualTo(id);
    assertThat(retrieved.getTitle()).isEqualTo("Success");
    assertThat(retrieved.getUrl()).isEqualTo("http://www.test.com");
  }

  @Test
  public void deleteBookmarkTest() throws Exception {
    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    response = client.delete("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    response = client.get("/api/bookmarks/" + id);
    assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  public void getBookmarksOrderTest() throws Exception {
    // delete existing bookmarks
    ReceivedResponse response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    for (Bookmark bookmark: bookmarks) {
      client.delete("/api/bookmarks/" + bookmark.getId());
    }

    // create new ones with titles in reverse order of creation time
    char[] initials = "ZYXWVUTSRQPONMLKJIHGFEDCBA".toCharArray();
    for (char initial: initials) {
      String title = initial + "_Title";
      String url = "http://www.test.com/" + initial;
      Bookmark bookmark = new Bookmark(title, url);
      client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    }

    response = client.get("/api/bookmarks?order=title");
    Bookmark[] bookmarksOrderByTitle = mapper.readValue(response.getBody().getText(),
        Bookmark[].class);

    response = client.get("/api/bookmarks?order=creation_timestamp");
    Bookmark[] bookmarksOrderByCreationTimestamp = mapper.readValue(response.getBody().getText(),
        Bookmark[].class);

    assertThat(initials.length).isEqualTo(bookmarksOrderByTitle.length);
    assertThat(initials.length).isEqualTo(bookmarksOrderByCreationTimestamp.length);

    int byTitlePos = 0;
    int byCreationTimestampPos = initials.length - 1;
    for (int i = 0; i < initials.length / 2; i++) {
      Bookmark byTitle = bookmarksOrderByTitle[byTitlePos + i];
      Bookmark byCreationTimestamp = bookmarksOrderByCreationTimestamp[byCreationTimestampPos - i];
      assertThat(byTitle.getId()).isEqualTo(byCreationTimestamp.getId());
      assertThat(byTitle.getTitle()).isEqualTo(byCreationTimestamp.getTitle());
      assertThat(byTitle.getUrl()).isEqualTo(byCreationTimestamp.getUrl());
      assertThat(byTitle.getCreationTimestamp()).isEqualTo(byCreationTimestamp.getCreationTimestamp());
    }
  }

  @Test
  public void freemarkerCreateBookmarkTest() throws Exception {
    ReceivedResponse response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    int lastSize = bookmarks.length;

    Bookmark  bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse createResponse =
        client.requestSpec(formRequestBody(bookmark)).post("/freemarker/bookmarks");

    assertThat(createResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);

    response = client.get("/api/bookmarks");
    bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    assertThat(bookmarks.length).isEqualTo(lastSize + 1);

    String expected = renderFreeMarker(bookmarks);
    String actual = createResponse.getBody().getText();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void freemarkerUpdateBookmarkTest() throws Exception {

    Bookmark bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    int lastSize = bookmarks.length;

    bookmark.setTitle("Updated");
    ReceivedResponse updateResponse =
        client.requestSpec(formRequestBody(bookmark, "put")).post("/freemarker/bookmarks/" + id);

    assertThat(updateResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    response = client.get("/api/bookmarks");
    bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    assertThat(bookmarks.length).isEqualTo(lastSize);

    String expected = renderFreeMarker(bookmarks);
    String actual = updateResponse.getBody().getText();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void freemarkerDeleteBookmarkTest() throws Exception {

    Bookmark bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]);

    response = client.get("/api/bookmarks");
    Bookmark[] bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    int lastSize = bookmarks.length;

    ReceivedResponse deleteResponse =
        client.requestSpec(formRequestBody(bookmark, "delete")).post("/freemarker/bookmarks/" + id);

    assertThat(deleteResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_OK);

    response = client.get("/api/bookmarks");
    bookmarks = mapper.readValue(response.getBody().getText(), Bookmark[].class);
    assertThat(bookmarks.length).isEqualTo(lastSize - 1);

    String expected = renderFreeMarker(bookmarks);
    String actual = deleteResponse.getBody().getText();
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void validateMissingTitleTest() throws JsonProcessingException {
    Bookmark bookmark = new Bookmark("", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    StrictAssertions.assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    StrictAssertions.assertThat(response.getBody().getText()).containsIgnoringCase("title");
  }

  @Test
  public void validateMissingUrlTest() throws JsonProcessingException {
    Bookmark bookmark = new Bookmark("Title", "");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    StrictAssertions.assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    StrictAssertions.assertThat(response.getBody().getText()).containsIgnoringCase("url");
  }

  @Test
  public void validateInvalidUrlTest() throws JsonProcessingException {
    Bookmark bookmark = new Bookmark("Title", "url");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    StrictAssertions.assertThat(response.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    StrictAssertions.assertThat(response.getBody().getText()).containsIgnoringCase("url");
  }

  @Test
  public void nonexistentBookmarkUpdateTest() throws JsonProcessingException {
    Bookmark bookmark = new Bookmark("Test", "http://www.test.com");
    ReceivedResponse response = client.requestSpec(jsonRequestBody(bookmark)).post("/api/bookmarks");
    String[] uri = response.getBody().getText().split("/");
    long id = Long.parseLong(uri[uri.length - 1]) + 1;

    bookmark.setTitle("Updated");
    ReceivedResponse updateResponse =
        client.requestSpec(formRequestBody(bookmark, "put")).post("/freemarker/bookmarks/" + id);
    StrictAssertions.assertThat(updateResponse.getStatus().getCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  private String renderFreeMarker(Bookmark[] bookmarks) throws IOException, TemplateException {
    Map<String, Object> model = new HashMap<>();
    model.put("bookmarks", bookmarks);
    model.put("content_template", "bookmark_list.ftl");

    StringWriter stringWriter = new StringWriter();
    Template template = freemarkerCfg.getTemplate("index.ftl");
    template.process(model, stringWriter);
    return stringWriter.toString();
  }

  private Action<? super RequestSpec> formRequestBody(Bookmark bookmark) {
    return formRequestBody(bookmark, null);
  }

  private Action<? super RequestSpec> formRequestBody(Bookmark bookmark, String method) {
    return requestSpec -> {
      StringBuilder sb = new StringBuilder();
      if (!StringUtils.isNullOrEmpty(method)) {
        sb.append("_method=").append(method);
      }
      String title = bookmark.getTitle();
      if (!StringUtils.isNullOrEmpty(title)) {
        if (sb.length() > 0) sb.append("&");
        sb.append("title=").append(title);
      }
      String url = bookmark.getUrl();
      if (!StringUtils.isNullOrEmpty(url)) {
        if (sb.length() > 0) sb.append("&");
        sb.append("url=").append(url);
      }
      requestSpec.getBody()
          .type(MediaType.APPLICATION_FORM)
          .text(sb.toString());
    };
  }
}
