# AndroidLibrary #

**Transform详解**

    * 1、Gradle插件包含了一个叫Transform的API，这个API允许第三方插件在class文件转为为dex文件前操作编译好的class文件，这个API的目标就是简化class文件的自定义的操作而不用对Task进行处理。
    * 2、Transform是作用在.class编译后，打包成.dex前，可以对.class和resource进行再处理的部分。

**自定义Android Gradle插件**

    * 1、在工程下新建一个module，名字必须为buildSrc或buildsrc，系统默认识别此目录
         * 1、在main目录下，新建groovy目录，在该文件下建立以.groovy结尾的文件。
         * 2、在main目录下，新建resources/META-INF/gradle-plugins目录，在该文件夹下创建一个以.properties结尾的文件，
              文件名是我们要引用的插件名称，文件内容：implementation-class=xxxxx（对应groovy目录下插件具体实现类）
    * 2、配置build.gradle内容：
         apply plugin: 'groovy'

         dependencies {
            // gradle sdk 使用项目中指定的gradle wrapper版本，插件中使用的Project对象等就来自这里
            implementation  gradleApi()
            // groovy sdk
            implementation  localGroovy()
            // Android编译的大部分gradle源码，比如TaskManager、ransform API
            implementation 'com.android.tools.build:gradle:3.2.0'
            implementation 'org.javassist:javassist:3.24.0-GA'
         }

    * 3、引用方式：
         * 1、引用以.properties结尾的文件名称：apply plugin:'com.xx.plugin'
         * 2、引用插件全类名：apply plugin:com.xx.plugin.XXXPlugin

**plugin-viewinject**

    * 此插件需结合 viewinject 注解工具使用，主要解决：
      1、主项目中 manifest packageName 和 applicationId 不一致时，无法查找控件id问题
      2、lib包无法使用viewinject工具
    * 执行步骤：
      1、定义 Gradle Plugin 插件，注册 Transform，同时获取 manifest packageName
      2、根据指定的 packageName，获取 Transform 输出的 R.class
      3、读取 R.class 中内部类 id.class，生成 Id.class，输出到指定的文件路径
      4、Gradle升级后，R.class文件不再输出，只存在于R.jar包中
