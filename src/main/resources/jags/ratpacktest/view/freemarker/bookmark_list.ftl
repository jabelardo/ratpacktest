<#escape x as x?html>
  <a href="/freemarker/bookmarks/new">Add New Bookmark</a> <h2>List of Bookmarks (FreeMarker)</h2>
  <ul>
    <#list bookmarks as bookmark>
      <li>
        <form action="/freemarker/bookmarks/${bookmark.id}" method="post">
          <a href="${bookmark.url}">${bookmark.title}</a> (<a href="/api/bookmarks/${bookmark.id}">${bookmark.id}</a>)
          <a href="/freemarker/bookmarks/${bookmark.id}">Edit</a>
          <input type="hidden" name="_method" value="delete">
          <input type="submit" value="Delete">
        </form>
      </li>
    </#list>
  </ul>
<br>
  <#if tags?? && (tags?size > 0)>
    <h2>Tags (<a href="/freemarker/bookmarks">All</a>)</h2>
    <ul>
      <#list tags as tag>
        <li>
            <a href="/freemarker/bookmarks?tags=${tag.label}">${tag.label}</a>
        </li>
      </#list>
    </ul>
  </#if>
</#escape>