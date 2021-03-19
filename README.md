# AndroidPlugin #

**plugin插件**

    * 1、plugin-injectclass：
         * 功能：用于修改主项目或jar中指定的class文件
    * 2、plugin-viewinject：
         * 功能：动态生成Id.class文件，解决viewinject注解工具无法在lib包使用问题
         * 注意：Gradle插件升级后，R.class文件不再输出，只存在于R.jar包中