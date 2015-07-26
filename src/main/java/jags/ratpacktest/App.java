package jags.ratpacktest;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.util.StringUtils;
import org.skife.jdbi.v2.DBI;
import ratpack.form.Form;
import ratpack.handling.Context;
import ratpack.http.MediaType;
import ratpack.jackson.Jackson;
import ratpack.render.RendererSupport;
import ratpack.server.RatpackServer;
import ratpack.util.MultiValueMap;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;

import javax.sql.DataSource;

public class App {

  private static DataSource ds = JdbcConnectionPool.create("jdbc:h2:mem:test", "sa", "");
  private static DBI dbi = new DBI(ds);
  private static Configuration freemarkerConfig;

  public static void main(String[] args) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    initDB();
    freemarkerConfig = initFreemarker();

    RatpackServer.start(server -> server
            .registryOf(
                registrySpec -> Jackson.Init.register(registrySpec, mapper, mapper.writer()))

            .handlers(chain -> chain

                    .get("hello", ctx -> ctx.render("Hello, Ratpack"))

                    .path("api/bookmarks", ctx -> ctx
                        .byMethod(method -> method
                            .post(() -> createBookmark(ctx))
                            .get(() -> getBookmarks(ctx))))

                    .path("api/bookmarks/:id", ctx -> ctx
                        .byMethod(method -> method
                            .delete(() -> deleteBookmark(ctx))
                            .put(() -> updateBookmark(ctx))
                            .get(() -> getBookmark(ctx))))

                    .register(new FreemarkerRenderer().register())

                    .path("freemarker/bookmarks", ctx -> ctx
                        .byMethod(method -> method
                            .get(() -> freemarkerBookmarkList(ctx))
                            .post(() -> freemarkerCreateBookmark(ctx))))

                    .get("freemarker/bookmarks/new", App::freemarkerBookmarkNew)

                    .path("freemarker/bookmarks/:id", ctx -> ctx
                        .byMethod(method -> method
                            .get(() -> freemarkerBookmarkEdit(ctx))
                            .post(() -> freemarkerUpdateOrDeleteBookmark(ctx))))
            )
    );
  }

  private static void deleteBookmark(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      dao.delete(id);
      ctx.getResponse().status(HttpURLConnection.HTTP_OK);
      ctx.getResponse().send();
    }
  }

  private static void getBookmark(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      Bookmark bookmark = dao.findOne(id);
      if (bookmark == null) {
        ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
        ctx.getResponse().send();
      } else {
        ctx.render(json(bookmark));
      }
    }
  }

  private static void updateBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      Bookmark bookmark = ctx.parse(fromJson(Bookmark.class));
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      validateForUpdate(bookmark);
      bookmark.setId(id);
      dao.update(bookmark);
      ctx.getResponse().status(HttpURLConnection.HTTP_NO_CONTENT);
      ctx.getResponse().send();
    }
  }

  private static void initDB() {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      dao.createBookmarkTable();
    } catch (Exception ignored) {
    }
  }

  private static void getBookmarks(Context ctx) {
    MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
    if ("creation_timestamp".equals(params.get("order"))) {
      getBookmarksOrderByByCreationTimestamp(ctx);
    } else {
      getBookmarksOrderByTitle(ctx);
    }
  }

  private static void getBookmarksOrderByTitle(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      ctx.render(json(dao.findOrderByTitle()));
    }
  }

  private static void getBookmarksOrderByByCreationTimestamp(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      ctx.render(json(dao.findOrderByCreationTimestamp()));
    }
  }

  private static void createBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      Bookmark bookmark = ctx.parse(fromJson(Bookmark.class));
      validateForCreate(bookmark);
      Long bookmarkId = dao.insert(bookmark);
      ctx.getResponse().status(HttpURLConnection.HTTP_CREATED);
      ctx.getResponse().send("/api/bookmarks/" + bookmarkId);
    }
  }

  private static void validateForUpdate(Bookmark bookmark) {
    if (StringUtils.isNullOrEmpty(bookmark.getTitle())) {
      throw new RuntimeException("title can't be empty");
    }
    if (StringUtils.isNullOrEmpty(bookmark.getUrl())) {
      throw new RuntimeException("url can't be empty");
    }
  }

  private static void validateForCreate(Bookmark bookmark) {
    if (bookmark.getId() != null) {
      throw new RuntimeException("id must be null");
    }
    validateForUpdate(bookmark);
  }

  public static Configuration initFreemarker() throws IOException {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_22);
    cfg.setClassForTemplateLoading(App.class, "freemarker");
    cfg.setDefaultEncoding("UTF-8");
    // During web page *development* TemplateExceptionHandler.HTML_DEBUG_HANDLER is better.
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
    // cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    return cfg;
  }

  private static void freemarkerBookmarkList(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      List<Bookmark> bookmarks = dao.findOrderByTitle();
      FreemarkerModel model = new FreemarkerModel();
      model.put("bookmarks", bookmarks);
      model.put("content_template", "bookmark_list.ftl");
      ctx.render(model);
    }
  }

  private static void freemarkerBookmarkNew(Context ctx) {
    FreemarkerModel model = new FreemarkerModel();
    model.put("bookmark", new Bookmark());
    model.put("content_template", "bookmark_form_new.ftl");
    ctx.render(model);
  }

  private static void freemarkerBookmarkEdit(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      FreemarkerModel model = new FreemarkerModel();
      Bookmark bookmark = dao.findOne(id);
      model.put("bookmark", bookmark);
      model.put("content_template", "bookmark_form_edit.ftl");
      ctx.render(model);
    }
  }

  private static void freemarkerCreateBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      Form form = ctx.parse(Form.class);
      String title = form.get("title");
      String url = form.get("url");
      Bookmark bookmark = new Bookmark(title, url);
      validateForCreate(bookmark);
      dao.insert(bookmark);
      ctx.getResponse().status(HttpURLConnection.HTTP_CREATED);
      ctx.insert(App::freemarkerBookmarkList);
    }
  }

  private static void freemarkerUpdateOrDeleteBookmark(Context ctx) throws Exception {
    Form form = ctx.parse(Form.class);
    String method = form.get("_method");
    if ("put".equals(method)) {
      freemarkerUpdateBookmark(ctx);
    }
    if ("delete".equals(method)) {
      freemarkerDeleteBookmark(ctx);
    } else {
      ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
    }
  }

  private static void freemarkerDeleteBookmark(Context ctx) {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      dao.delete(id);
      ctx.getResponse().status(HttpURLConnection.HTTP_OK);
      ctx.insert(App::freemarkerBookmarkList);
    }
  }

  private static void freemarkerUpdateBookmark(Context ctx) throws Exception {
    try (BookmarkDAO dao = dbi.open(BookmarkDAO.class)) {
      long id = Long.parseLong(ctx.getPathTokens().get("id"));
      Form form = ctx.parse(Form.class);
      String title = form.get("title");
      String url = form.get("url");
      Bookmark bookmark = new Bookmark(id, title, url);
      validateForUpdate(bookmark);
      dao.update(bookmark);
      ctx.getResponse().status(HttpURLConnection.HTTP_OK);
      ctx.insert(App::freemarkerBookmarkList);
    }
  }

  private static class FreemarkerRenderer extends RendererSupport<FreemarkerModel> {
    @Override
    public void render(Context ctx, FreemarkerModel model) throws Exception {
      StringWriter stringWriter = new StringWriter();

      Template template = freemarkerConfig.getTemplate("index.ftl");
      template.process(model, stringWriter);

      ctx.getResponse().contentType(MediaType.TEXT_HTML);
      ctx.getResponse().send(stringWriter.toString());
    }
  }

  private static class FreemarkerModel extends HashMap<String, Object> {}
}
