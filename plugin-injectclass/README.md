# AndroidLibrary #

**自定义Android Gradle插件**

    * 1、在工程下新建一个module，名字必须为buildSrc或buildsrc，系统默认识别此目录
         * 1、在main目录下，新建groovy目录，在该文件下建立以.groovy结尾的文件。
         * 2、在main目录下，新建resources/META-INF/gradle-plugins目录，在该文件夹下创建一个以.properties结尾的文件，
              文件名是我们要引用的插件名称，文件内容：implementation-class=xxxxx（对应groovy目录下插件具体实现类）
    * 2、配置build.gradle内容：
         apply plugin: 'groovy'

         dependencies {
             //gradle sdk
             compile gradleApi()
             //groovy sdk
             compile localGroovy()
         }

    * 3、引用方式：
         * 1、引用以.properties结尾的文件名称：apply plugin:'com.xx.plugin'
         * 2、引用插件全类名：apply plugin:com.xx.plugin.XXXPlugin

**plugin-injectclass**

    * 此插件主要用于修改主项目或jar中指定的class文件
    * 1、在 build.gradle 文件中配置插件参数
            injectClass {
                dirClass "xx.xx.xx.xx"
                dirCode "xxxxxxxxxxxx"
                jarName "xx.xx.xx.xx"
                jarClass "xx.xx.xx.xx"
                jarCode "xxxxxxxxxxxx"
            }
    * 2、定义Gradle Plugin插件，注册Transform，同时获取 build.gradle 文件中的配置信息
    * 3、获取Transform输出的dir和jar包目录，修改指定的class文件
