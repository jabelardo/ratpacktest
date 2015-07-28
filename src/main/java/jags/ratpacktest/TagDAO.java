package jags.ratpacktest;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import java.util.List;

/**
 * Created by jose abelardo gutierrez on 7/27/15.
 */
public interface TagDAO extends AutoCloseable {

  @Override
  void close();

  @SqlUpdate("create table tag("
      + "id identity primary key,"
      + "label varchar(80) not null)")
  void createTagTable();

  @SqlQuery("select id, label from tag join tagging on(tag.id = tagging.tag_id) "
      + "where tagging.bookmark_id = :id")
  @Mapper(Tag.Mapper.class)
  List<Tag> findByBookmarkId(@Bind("id") Long id);

  @SqlUpdate("insert into tag(label) values(:label)")
  @GetGeneratedKeys
  long insert(@BindBean Tag tag);

  @SqlUpdate("delete from tag where id = :id")
  void deleteById(@Bind("id") Long id);

  @SqlQuery("select id, label from tag where label = :label")
  @Mapper(Tag.Mapper.class)
  Tag findByLabel(@Bind("label") String label);

  @SqlQuery("select id, label from tag order by label")
  @Mapper(Tag.Mapper.class)
  List<Tag> findOrderByLabel();

  @SqlQuery("select count(*) from tag")
  int count();

  @SqlUpdate("delete from tag")
  void delete();
}
