# codeanalyser
svn code analyser for testbird TROC team
# 如何构建
* 使用gradle命令构建`gradle clean canalyDistZip`
* 构建成功后, zip包位置在`build/distributions/code-analysis.zip`
# 如何运行
* 解压`code-analysis.zip`
* 创建`service.env`配置文件, 可以从模版拷贝`copy service.env.template service.env`
* 配置`TB_NET_INTERFACE_NAME`为当前网卡名称, 可通过`ifconfig`命令查看， *其他基本无需改动*
* `keywords_analysis.json` 文件中配置了需要扫描的文件的后缀和忽略文件等信息， *基本无需改动*
* 启动服务`./run_code_analysis.sh server`
# 功能说明
* Main-class为`com.testbird.util.codeanalyser.Main`
* Main启动RestFul的http server，监听`/keywords/search`和`/keywords/stop`接口请求. 
* `/keywords/search`接口的请求参数为json格式：
```json
{
  "type":"repo", "key": "xxx", "keywords":[], "regExpKeywords": [],
  "repoInfo": { "repoType":"svn", "repoAddr":"svn://...","username":"name","password":"123","lastRepoVersion":"1234","repoVersion":"1234"}
}
```
or
```
{"type":"file", "key": "xxx", "keywords":[], "regExpKeywords": [], "href"="file.lab.tb/upload/xxxx.zip"}
```
* `/keywords/search`请求在`com.testbird.util.codeanalyser.KeywordsAnalyseController`类的`searchKeywords`方法处理.
* `/keywords/stop`请求在`com.testbird.util.codeanalyser.KeywordsAnalyseController`类的`stopSearchKeywords`方法处理.
# 扫描流程
* 先根据项目svn地址，checkout到本地
* 扫描敏感词
* 记录当前版本与`lastRepoVersion`之间的差异并记录`versionLogs`
* 记录项目`lastChangeTime`和当前`repoVersion`
* 上传扫描结果到服务器，*API地址配置在`service.env`中配置的`KEYWORDS_SCANNER_RESPONSE_NOTIF_URL`变量*，
  post内容格式：`com.testbird.util.codeanalyser.SearchKeywordsResponse`
* 上传结果文件到fileServer，*fileServer地址配置在`service.env`中的`FILE_SERVER_URL`变量*
