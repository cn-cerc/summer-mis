# summer-controller

对应MVC的控制器层，具体说明和使用方式详见以下模组

# summer-mis 项目简介

资讯管理系统（MIS）是企业、政府应用最大的软件类别。此类系统均有一些共性的功能，如帐套管理、菜单管理、权限管理等，我们没有必要每次都重头开发，重头开发既不能控制交期，也难以保障品质。

`summer-mis` 旨在建立一个公共的MIS二次开发平台，可基于此快速建立类似的，并运行在云平台的大数据应用，以降低开发成本，控制交付日期。项目代码会聚各类开发人员进行不断迭代升级，已成功使用在不同的商业软件项目。

`summer-mis` 采用约定大于配置的策略，严格遵守开源软件的规则，并尽力保障向后兼容，充分保护大家的开发成果与可延续性。

核心对象主要有IForm与IService，二者结合可低成本地实现微服务架构，同时保障系统功能弹性与性能弹性：

* IForm，定位于页面控制器，用于接收web输入，以及输出IPage接口。其中IPage实现有：JspPage、JsonPage、RedirectPage等，可自由扩充。实际编写时，可直接继承AbstractForm后快速实现具体的页面控制器。

* IService，定位于业务逻辑，用于接收web输出，以及输出IStatus与DataSet-JSON，并可通过包装类，转化为其它格式如xml的输出，此项与IForm的差别在于：IForm有提供对getRequest().getSession()的访问，可使用HttpSession。IService有提供RESTful接口，可提供第三方访问。

实际使用时，IForm会调用IService，而IService既对内提供业务服务（微服务），也对外提供业务服务。

欢迎大家使用，同时反馈更多的建议与意见，也欢迎其它业内人士，对此项目进行协同改进！
