package jags.ratpacktest;

import static ratpack.jackson.Jackson.fromJson;
import static ratpack.jackson.Jackson.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import jags.ratpacktest.domain.Bookmark;
import jags.ratpacktest.domain.Tag;
import jags.ratpacktest.exception.ValidationException;
import jags.ratpacktest.service.BookmarkService;
import jags.ratpacktest.view.FreemarkerModel;
import jags.ratpacktest.view.FreemarkerRenderer;
import ratpack.form.Form;
import ratpack.handling.Context;
import ratpack.jackson.Jackson;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;
import ratpack.util.MultiValueMap;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class App {

  private static BookmarkService bookmarkService = new BookmarkService();

  public static void main(String[] args) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.enable(SerializationFeature.INDENT_OUTPUT);



    RatpackServer.start(server -> {

          URL publicResource = App.class.getClassLoader().getResource("public/public.resource");
          assert publicResource != null;
          File publicPath = new File(publicResource.getFile()).getParentFile();

          server
              .serverConfig(ServerConfig.baseDir(publicPath))

              .registryOf(
                  registrySpec -> Jackson.Init.register(registrySpec, mapper, mapper.writer()))

              .handlers(chain -> chain

                      .files(fileHandler -> fileHandler.indexFiles("index.html"))

                      .get("", ctx -> ctx.redirect("canjs"))

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

                      .get("api/tags", App::getTags)

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
              );
        }
    );
  }

  private static void deleteBookmark(Context ctx) {
    long id = Long.parseLong(ctx.getPathTokens().get("id"));
    bookmarkService.deleteBookmark(id);
    ctx.getResponse().status(HttpURLConnection.HTTP_OK);
    ctx.getResponse().send();
  }

  private static void getBookmark(Context ctx) {
    long id = Long.parseLong(ctx.getPathTokens().get("id"));
    Bookmark bookmark = bookmarkService.getBookmark(id);
    if (bookmark == null) {
      ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
      ctx.getResponse().send();
    } else {
      ctx.render(json(bookmark));
    }
  }

  private static void updateBookmark(Context ctx) {
    try {
      Bookmark bookmark = ctx.parse(fromJson(Bookmark.class));
      bookmark.setId(Long.parseLong(ctx.getPathTokens().get("id")));
      Bookmark updated = bookmarkService.updateBookmark(bookmark);
      if (updated == null) {
        ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
        ctx.getResponse().send();
      } else {
        ctx.getResponse().status(HttpURLConnection.HTTP_NO_CONTENT);
        ctx.getResponse().send();
      }
    } catch (ValidationException e) {
      ctx.getResponse().send(e.getMessage());
      ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void createBookmark(Context ctx) {
    try {
      Bookmark bookmark = ctx.parse(fromJson(Bookmark.class));
      Bookmark created = bookmarkService.createBookmark(bookmark);
      ctx.getResponse().status(HttpURLConnection.HTTP_CREATED);
      ctx.getResponse().send("/api/bookmarks/" + created.getId());
    } catch (ValidationException e) {
      ctx.getResponse().status(HttpURLConnection.HTTP_BAD_REQUEST);
      ctx.getResponse().send(e.getMessage());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void getTags(Context ctx) {
    List<Tag> tags = bookmarkService.getTags();
    ctx.render(json(tags));
  }

  private static void getBookmarks(Context ctx) {
    MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
    String tags = params.get("tags");
    String order = params.get("order");
    List<Bookmark> bookmarks = bookmarkService.getBookmarksOrderByTitle(tags, order);
    ctx.render(json(bookmarks));
  }

  private static void freemarkerBookmarkList(Context ctx) {
    MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
    String tags = params.get("tags");
    String order = params.get("order");
    List<Bookmark> bookmarks = bookmarkService.getBookmarksOrderByTitle(tags, order);
    List<Tag> tagList = bookmarkService.getTags();
    FreemarkerModel model = new FreemarkerModel();
    model.put("bookmarks", bookmarks);
    model.put("tags", tagList);
    model.put("content_template", "bookmark_list.ftl");
    ctx.render(model);
  }

  private static void freemarkerBookmarkNew(Context ctx) {
    FreemarkerModel model = new FreemarkerModel();
    model.put("bookmark", new Bookmark());
    model.put("content_template", "bookmark_form_new.ftl");
    ctx.render(model);
  }

  private static void freemarkerBookmarkEdit(Context ctx) {
    long id = Long.parseLong(ctx.getPathTokens().get("id"));
    Bookmark bookmark = bookmarkService.getBookmark(id);
    if (bookmark == null) {
      ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
      ctx.getResponse().send();
    } else {
      FreemarkerModel model = new FreemarkerModel();
      model.put("bookmark", bookmark);
      model.put("content_template", "bookmark_form_edit.ftl");
      ctx.render(model);
    }
  }

  private static void freemarkerCreateBookmark(Context ctx) throws Exception {
    Form form = ctx.parse(Form.class);
    String title = form.get("title");
    String url = form.get("url");
    String tags = form.get("tags");
    bookmarkService.createBookmark(new Bookmark(title, url, tags));
    ctx.getResponse().status(HttpURLConnection.HTTP_CREATED);
    ctx.insert(App::freemarkerBookmarkList);
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
    long id = Long.parseLong(ctx.getPathTokens().get("id"));
    bookmarkService.deleteBookmark(id);
    ctx.getResponse().status(HttpURLConnection.HTTP_OK);
    ctx.insert(App::freemarkerBookmarkList);
  }

  private static void freemarkerUpdateBookmark(Context ctx) throws Exception {
    long id = Long.parseLong(ctx.getPathTokens().get("id"));
    Form form = ctx.parse(Form.class);
    String title = form.get("title");
    String url = form.get("url");
    String tags = form.get("tags");
    Bookmark bookmark = bookmarkService.updateBookmark(new Bookmark(id, title, url, tags));
    if (bookmark == null) {
      ctx.getResponse().status(HttpURLConnection.HTTP_NOT_FOUND);
      ctx.getResponse().send();
    } else {
      ctx.getResponse().status(HttpURLConnection.HTTP_OK);
      ctx.insert(App::freemarkerBookmarkList);
    }
  }

  public static void addTags(Bookmark bookmark) {
    bookmarkService.addTags(bookmark);
  }


}
