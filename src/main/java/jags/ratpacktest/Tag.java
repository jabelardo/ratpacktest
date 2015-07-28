package jags.ratpacktest;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by jose abelardo gutierrez on 7/27/15.
 */
public class Tag {
  private Long id;
  private String label;

  public Tag() {
  }

  public Tag(String label) {
    this.label = label;
  }

  public Tag(Long id, String label) {
    this.id = id;
    this.label = label;
  }

  public Long getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public void setId(long id) {
    this.id = id;
  }

  public static class Mapper implements ResultSetMapper<Tag> {
    @Override
    public Tag map(int idx, ResultSet rs, StatementContext sc)
        throws SQLException {
      return new Tag(rs.getLong("id"), rs.getString("label"));
    }
  }
}
