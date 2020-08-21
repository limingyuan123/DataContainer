
# DataContainer
@[TOC]
## 批量上传接口
* 请求方式
```POST```
* 接口参数

```BODY formdata```
**************
|字段|示例值|
|--|--|
| name|test  |
| userId|1  |
| serverNode|china|
| origination|developer|

* 接口示例
```
curl --location --request POST 'http://221.226.60.2:8082/data' \
--form 'ogmsdata=@/path/to/file' \
--form 'name=test' \
--form 'userId=1' \
--form 'serverNode=china' \
--form 'origination=developer'
```
* 返回值
```json
{"code":0,"msg":"success","data":{"file_name":"test","source_store_id":"893b08ef-73ab-4d29-aaa9-239bc3115001"}}
```
## 下载接口
* 请求方式
```GET```
* 接口参数
```PARAMS```
*************
| 字段  |示例值  |
|---|--|
|uid|893b08ef-73ab-4d29-aaa9-239bc3115001  |

* 接口示例
```
http://221.226.60.2:8082/data?uid=893b08ef-73ab-4d29-aaa9-239bc3115001
```
* 返回值
下载的数据，无返回值
## 批量下载接口
* 请求方式
```GET```
* 接口参数
```PARAMS```
*************
| 字段  |示例值  |
|---|--|
|oids|b5d92fa5-edf8-4385-aaa9-d2015928a047,92614796-6a5d-4467-b2e2-959dcaa59016  |

* 接口示例
```
http://221.226.60.2:8082/bulkDownload?oids=b5d92fa5-edf8-4385-aaa9-d2015928a047,92614796-6a5d-4467-b2e2-959dcaa59016
```
* 返回值
下载的zip包，无返回值
## 删除接口
* 请求方式
```DELETE```
* 接口参数
```PARAMS```
*************
| 字段  |示例值  |
|---|--|
|uid|893b08ef-73ab-4d29-aaa9-239bc3115001  |

* 接口示例
```
http://221.226.60.2:8082/del?uid=893b08ef-73ab-4d29-aaa9-239bc3115001
```
* 返回值
```json
{
    "code": 0,
    "msg": "delete success",
    "data": null
}
```
## 批量删除接口
* 请求方式
```DELETE```
* 接口参数
```PARAMS```
*************
| 字段  |示例值  |
|---|--|
|oids|b5d92fa5-edf8-4385-aaa9-d2015928a047,92614796-6a5d-4467-b2e2-959dcaa59016  |
* 接口示例
```
http://221.226.60.2:8082/bulkDel?oids=b5d92fa5-edf8-4385-aaa9-d2015928a047,92614796-6a5d-4467-b2e2-959dcaa59016
```
* 返回值
```json
{
    "code": 0,
    "msg": "All fail delete success",
    "data": null
}
```
## 可视化接口
* 请求方式
```GET```
* 接口参数
```PARAMS```
*************
| 字段  |示例值  |
|---|--|
|uid|e684ef96-cbad-468d-853e-e80fe157fbf5  |
* 接口示例
```
http://221.226.60.2:8082/visual?uid=e684ef96-cbad-468d-853e-e80fe157fbf5
```
* 返回值
下载的png文件，无返回值
## 无需配置文件上传接口
* 请求方式
```POST```
* 接口参数
```BODY formdata```
**************
|字段|示例值|
|--|--|
| name|test  |
| userId|1  |
| serverNode|china|
| origination|developer|
* 接口示例
```
curl --location --request POST 'http://221.226.60.2:8082/dataNoneConfig' \
--form 'ogmsdata=@/path/to/file' \
--form 'name=test' \
--form 'userId=1' \
--form 'serverNode=china' \
--form 'origination=developer'
```
* 返回值
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "file_name": "test",
        "source_store_id": "25ec8bd5-0bbc-4507-a6b4-2473cb99e970"
    }
}
```
## 更改dataTemplate接口
* 请求方式
```POST```
* 接口参数
```PARAMS```
*************
| 字段  |示例值  |意义|
|---|--|--|
|oid|e684ef96-cbad-468d-853e-e80fe157fbf5  |数据的id|
|templateId|d3605b83-af8d-491c-91b3-a0e0bf3fe714  |需要新增或者更改的templateId|
|type|add or type  |操作的类型|
* 接口示例
```
http://221.226.60.2:8082/editTemplateId?oid=af44f4c9-da19-480f-9253-f21250fa10a5&templateId=d3605b83-af8d-491c-91b3-a0e0bf3fe7&type=add
```
* 返回值
新增成功：
```json
{
    "code": 0,
    "msg": "add success",
    "data": "oid is af44f4c9-da19-480f-9253-f21250fa10a5"
}
```
编辑成功
```json
{
    "code": 0,
    "msg": "edit success",
    "data": "oid is af44f4c9-da19-480f-9253-f21250fa10a5"
}
```
## 全局搜索接口
* 请求方式
```GET```
* 接口参数
```PARAMS```
*************
| 字段  |示例值  |意义|
|---|--|--|
|name|shp  |全局搜索的关键字名称|
* 接口示例
```
http://221.226.60.2:8082/globalSearch?name=test特殊符号
```
* 返回值
```json
{
    "code": 0,
    "msg": "success",
    "data": [
        {
            "name": "test特殊符号",
            "oid": "5d96a719-d260-4f1b-99c7-19f22b337de8"
        },
        {
            "name": "test特殊符号",
            "oid": "5d96a719-d260-4f1b-99c7-19f22b337de8"
        }
    ]
}
```
## 配置文件
### 字段
```
<UDXZip>
    <Name> 文件列表，不包含配置文件，数目要上传文件数一致（不包含配置文件）
        <add value:文件名>
        ...
    </Name>   
    <DataTemplate    type:数据类型，可选参数 id, schema, none > 数据类型id,在type为id时有值</DataTemplate>
</UDXZip>
```
### 基本内容
```
<UDXZip>
	<Name>//列出文件名，文件名不需要一一对应，但文件个数要和实际上传文件数对应
		<add value="dem.prj" />
		 <add value="dem.tif" />
	</Name>
    // type 参数可为id，schema，none,分别表示raw data、schema data 和其他任意数据
    //此尖括号下的内容为对应的数据模板的id,目前只有三种id可选，分别代表type=id的两种数据，shp和tiff
    //shp:['4996e027-209b-4121-907b-1ed36a417d22'],
    //tiff:['d3605b83-af8d-491c-91b3-a0e0bf3fe714','f73f31ff-2f23-4c7a-a57d-39d0c7a6c4e6']
    //此例中的id是tiff数据
	<DataTemplate type="id">d3605b83-af8d-491c-91b3-a0e0bf3fe714</DataTemplate>
</UDXZip>
```
### 可选模板
```
主要在type为id的数据类型，进行可视化时使用

shp:['4996e027-209b-4121-907b-1ed36a417d22'],
tiff:['d3605b83-af8d-491c-91b3-a0e0bf3fe714','f73f31ff-2f23-4c7a-a57d-39d0c7a6c4e6']
```
