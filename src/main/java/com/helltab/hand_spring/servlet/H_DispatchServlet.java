package com.helltab.hand_spring.servlet;

import com.helltab.hand_spring.annotation.H_Autowired;
import com.helltab.hand_spring.annotation.H_Controller;
import com.helltab.hand_spring.annotation.H_RequestMapping;
import com.helltab.hand_spring.annotation.H_Service;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 自定义 DispatchServlet
 *
 * @WebServlet("/") 加了注解便不用在 web.xml 中定义了
 */
@WebServlet("/")
public class H_DispatchServlet extends HttpServlet {
    /**
     * 用于获取资源信息
     */
    private final ClassLoader classLoader = getClass().getClassLoader();

    /**
     * 处理器映射集合
     */
    private final Map<String, Method> handlerMapping = new HashMap<>();

    /**
     * 扫描出的所有 class 集合
     */
    private final List<String> classNameList = new ArrayList<>();


    /**
     * ioc 容器
     */
    private final Map<String, Object> iocMap = new HashMap<>();

    /**
     * 处理 classPath 下面的配置文件
     */
    private final Properties contextConfig = new Properties();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Object o = doDispatch(req, resp);
            assert o != null;
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().write(o.toString());
        } catch (Exception e) {
            resp.getWriter().write("500: " + Arrays.toString(e.getStackTrace()));
        }
    }


    /**
     * 处理请求映射
     */
    private Object doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        System.out.println(req.getPathInfo());
        String contextPath = req.getContextPath();
        String path = req.getServletPath().replace(contextPath, "");
        if (handlerMapping.containsKey(path)) {
            try {
                Method method = handlerMapping.get(path);
                String controllerBeanName = firstToLowcase(method.getDeclaringClass().getSimpleName());
                Object controllerBean = iocMap.get(controllerBeanName);
                return method.invoke(controllerBean);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 初始化操作
     * 初始化是第一次接收到请求的时候做的
     *
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1. 配置, 使用注解就直接写配置文件名称
//        String configFile = config.getInitParameter("contextLocation");
//        doConfig(configFile);
        doConfig("application.properties");
        // 2. 扫描类
        doScanPackage(contextConfig.getProperty("scan-package"));
        // 3. 初始化 ioc 容器
        initIocContainer();
        // 4. 注入依赖
        initAutowired();
        // 5. 初始化 handlerMapping
        initHandlerMapping();
        System.out.println("dispatch init completed");
    }


    /**
     * 提取映射
     * 将 controller 类上面的路径加上方法上的路径作为请求的 url
     */
    private void initHandlerMapping() {
        iocMap.forEach((beanName, bean) -> {
            Class<?> beanClass = bean.getClass();
            if (beanClass.isAnnotationPresent(H_Controller.class)) {
                String baseUrl = "";
                if (beanClass.isAnnotationPresent(H_RequestMapping.class)) {
                    baseUrl = beanClass.getAnnotation(H_RequestMapping.class).value();
                }
                for (Method method : beanClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(H_RequestMapping.class)) {
                        String url = "/" + baseUrl + "/" + method.getAnnotation(H_RequestMapping.class).value();
                        url = url.replaceAll("/+", "/");
                        handlerMapping.put(url, method);
                        System.out.println("[I-05] and handler mapping: " + url);
                    }
                }
            }
        });
    }

    /**
     * 自动注入
     */
    private void initAutowired() {
        iocMap.forEach((beanName, bean) -> {
            Field[] fields = bean.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(H_Autowired.class)) {
                    String value = field.getAnnotation(H_Autowired.class).value();
                    String name = "".equals(value) ? field.getName() : value;
                    if (!iocMap.containsKey(name)) {
                        try {
                            throw new Exception("could not find bean: " + name);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        // 注入依赖
                        field.setAccessible(true);
                        field.set(bean, iocMap.get(name));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        System.out.println("[I-04] auto wired completed");
    }

    /**
     * 将首字母变为小写
     *
     * @param org
     * @return
     */
    private String firstToLowcase(String org) {
        if (org.length() > 0) {
            org = org.substring(0, 1).toLowerCase() + org.substring(1);
        }
        return org;
    }


    /**
     * 保存到 iocMap 中, 单独提取出来可以处理打印逻辑
     *
     * @param name
     * @param instance
     */
    private void saveToIocMap(String name, Object instance) {
        System.out.println("[I-03] add to ioc: " + name);
        iocMap.put(name, instance);
    }

    /**
     * 初始化 ioc 容器
     * 1. 从扫描的 class 集合过滤出 Controller Service 注解的 class
     * 2. 将 class 的简单名称首字母小写作为 beanName, 将实例存入 iocMap
     * 3. 如果是 service, 如果有 value, 需要根据 value 的值来保存
     * 4. service 的接口集合也需要进行注入, 一般是注入第一个实现类的实例
     */
    private void initIocContainer() {
        classNameList.forEach(className -> {
            try {
                Class<?> aClass = Class.forName(className);
                // controller
                if (aClass.isAnnotationPresent(H_Controller.class)) {
                    Object instance = aClass.getDeclaredConstructor().newInstance();
                    saveToIocMap(firstToLowcase(aClass.getSimpleName()), instance);
                }

                // service
                if (aClass.isAnnotationPresent(H_Service.class)) {
                    String value = aClass.getAnnotation(H_Service.class).value();
                    String beanName = "".equals(value) ? firstToLowcase(aClass.getSimpleName()) : value;
                    Object instance = aClass.getDeclaredConstructor().newInstance();
                    saveToIocMap(beanName, instance);
                    for (Class<?> anInterface : aClass.getInterfaces()) {
                        String iBeanName = firstToLowcase(anInterface.getSimpleName());
                        if (!iocMap.containsKey(iBeanName)) {
                            saveToIocMap(iBeanName, instance);
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }


    /**
     * 扫描所有的 class
     * 注意目录需要递归处理
     *
     * @param basePackage
     */
    private void doScanPackage(String basePackage) {
        String basePath = basePackage.replaceAll("\\.", "/");
        String packagePath = classLoader.getResource(basePath).getFile();
        File file = new File(packagePath);
        for (File f : file.listFiles()) {
            if (f.isDirectory()) {
                doScanPackage(basePackage + "." + f.getName());
            } else {
                if (f.getName().endsWith(".class")) {
                    classNameList.add(basePackage + "." + f.getName().replace(".class", ""));
                    System.out.println("[I-02] class scanned :" + f.getName());
                }
            }
        }

    }


    /**
     * 读取配置信息
     *
     * @param configFile
     */
    private void doConfig(@NotNull String configFile) {
        InputStream inputStream = classLoader.getResourceAsStream(configFile);
        try {
            contextConfig.load(inputStream);
            System.out.println("[I-01] config loaded");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
