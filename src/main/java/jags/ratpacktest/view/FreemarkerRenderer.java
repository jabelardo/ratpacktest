package jags.ratpacktest.view;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import ratpack.handling.Context;
import ratpack.http.MediaType;
import ratpack.render.RendererSupport;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Created by jose abelardo gutierrez on 7/30/15.
 */
public class FreemarkerRenderer extends RendererSupport<FreemarkerModel> {

  private Configuration freemarkerConfig;

  public FreemarkerRenderer() throws IOException {
    freemarkerConfig = new Configuration(Configuration.VERSION_2_3_22);
    freemarkerConfig.setClassForTemplateLoading(FreemarkerRenderer.class, "freemarker");
    freemarkerConfig.setDefaultEncoding("UTF-8");
    // During web page *development* TemplateExceptionHandler.HTML_DEBUG_HANDLER is better.
    freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
    // cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
  }

  public Configuration getFreemarkerConfig() {
    return freemarkerConfig;
  }

  @Override
  public void render(Context ctx, FreemarkerModel model) throws Exception {
    StringWriter stringWriter = new StringWriter();
    Template template = freemarkerConfig.getTemplate("index.ftl");
    template.process(model, stringWriter);
    ctx.getResponse().contentType(MediaType.TEXT_HTML);
    ctx.getResponse().send(stringWriter.toString());
  }
}
