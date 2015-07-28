package jags.ratpacktest;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by jose abelardo gutierrez on 7/27/15.
 */
public class Tagging {
  private Long bookmarkId;
  private Long tagId;

  public Tagging(Long bookmarkId, Long tagId) {
    this.bookmarkId = bookmarkId;
    this.tagId = tagId;
  }

  public Long getBookmarkId() {
    return bookmarkId;
  }

  public Long getTagId() {
    return tagId;
  }

  public static class Mapper implements ResultSetMapper<Tagging> {
    @Override
    public Tagging map(int idx, ResultSet rs, StatementContext sc)
        throws SQLException {
      return new Tagging(rs.getLong("bookmark_id"), rs.getLong("tag_id"));
    }
  }
}
