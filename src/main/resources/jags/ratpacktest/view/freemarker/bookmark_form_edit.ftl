<#escape x as x?html>
  <h2>Edit Bookmark</h2>
  <form action="/freemarker/bookmarks/${bookmark.id}" method="post">
    <input type="hidden" name="_method" value="put">
    <#include "bookmark_form_inputs.ftl">
  </form>
</#escape>