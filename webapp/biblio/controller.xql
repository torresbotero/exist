xquery version "1.0";

declare variable $local:CREDENTIALS := ("guest", "guest");

declare function local:logout() {
    let $logout := request:get-parameter("logout", ())
    return
        if ($logout) then
            session:clear()
        else
            ()
};

declare function local:set-user() {
    session:create(),
    local:logout(),
    let $user := request:get-parameter("user", ())
    let $password := request:get-parameter("password", ())
    let $sessionUser := session:get-attribute("biblio.user")
    return
        if ($user) then (
            session:set-attribute("biblio.user", $user),
            session:set-attribute("biblio.password", $password),
            <set-attribute name="xquery.user" value="{$user}"/>,
            <set-attribute name="xquery.password" value="{$password}"/>
        ) else if ($sessionUser != '') then (
            <set-attribute name="xquery.user" value="{$sessionUser}"/>,
            <set-attribute name="xquery.password" value="{session:get-attribute('biblio.password')}"/>
        ) else (
            <set-attribute name="xquery.user" value="{$local:CREDENTIALS[1]}"/>,
            <set-attribute name="xquery.password" value="{$local:CREDENTIALS[2]}"/>
        )
};

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="index.xml"/>
	</dispatch>
(:  Main page: index.xml is a template, which is passed through
    search.xql and the db2xhtml stylesheet. search.xql will run
    the actual search and expand the index.xml template.
:)
else if ($exist:resource eq 'index.xml') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        { local:set-user() }
        <view>
            <forward url="search.xql">
                <!-- Errors should be passed through instead of terminating the request -->
        		<set-attribute name="xquery.report-errors" value="yes"/>
            </forward>
		</view>
	</dispatch>
(:  Retrieve an item from the query results stored in the HTTP session. The
	format of the URL will be /sandbox/results/X, where X is the number of the
	item in the result set :)
else if ($exist:resource eq 'retrieve') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
	   { local:set-user() }
		<forward url="{$exist:controller}/session.xql">
		</forward>
	</dispatch>
else if (matches($exist:path, '(resources/|styles/syntax|scripts/|logo.jpg|default-style2.css|curvycorners.js)')) then
    let $newPath := replace($exist:path, '^.*((styles/|scripts/|logo).*)$', '/$1')
    return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="{$newPath}"/>
    		<cache-control cache="yes"/>
    	</dispatch>
else
    (: everything else is passed through :)
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        { local:set-user() }
        <cache-control cache="yes"/>
    </dispatch>
