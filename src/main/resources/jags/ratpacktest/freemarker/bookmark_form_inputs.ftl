<#escape x as x?html>
  <label>
    URL:
    <input type="text" name="url" value="${bookmark.url ! ""}">
  </label>
  <label>
    Title:
    <input type="text" name="title" value="${bookmark.title ! ""}">
  </label>
  <input type="submit" name="save" value="Save"> <a href="/freemarker/bookmarks">Cancel</a>
</#escape>