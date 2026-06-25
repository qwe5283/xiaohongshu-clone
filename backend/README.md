# 小红书(复刻)后端服务

这是一个学习项目，基于 Spring Boot 复刻的的小红书后端服务。

## 技术栈

- **框架**: Spring Boot 3.5.5
- **ORM**: MyBatis-Plus 3.5.5
- **数据库**: MySQL 8.0+
- **缓存**: Redis
- **鉴权**: Spring Security + JWT
- **文件存储**: MinIO
- **工具**: Hutool, Lombok

## 项目结构

```
backend/
├── src/main/java/com/xiaohongshu/
│   ├── XiaohongshuApplication.java    # 启动类
│   ├── common/                        # 通用类
│   │   ├── exception/                 # 异常处理
│   │   └── result/                    # 统一响应
│   ├── config/                        # 配置类
│   ├── controller/                    # 控制器
│   ├── dto/                           # 数据传输对象
│   ├── entity/                        # 实体类
│   ├── mapper/                        # Mapper接口
│   ├── security/                      # 安全相关（JWT）
│   ├── service/                       # 服务接口
│   │   └── impl/                      # 服务实现
│   └── vo/                            # 视图对象
├── src/main/resources/
│   ├── application.yml                # 配置文件
│   └── schema.sql                     # 数据库脚本
└── pom.xml                            # Maven配置
```

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

## 快速开始

### 1. 初始化数据库

```bash
# 登录MySQL并执行SQL脚本
mysql -u root -p < src/main/resources/schema.sql
```

### 2. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库和Redis连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/xiaohongshu
    username: your_username
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
```

### 3. 编译运行

```bash
# 编译项目
mvn clean package

# 运行项目
java -jar target/xiaohongshu-backend-1.0.0.jar

# 或者使用Maven直接运行
mvn spring-boot:run
```

服务启动后访问: http://localhost:8080

## API 接口文档

参考`docs/API接口文档.md`

## 测试账号

系统初始化后会创建以下测试账号（密码均为 `123456`）：

| 用户名 | 昵称 | 角色 |
|--------|------|------|
| admin | 管理员 | 系统管理员 |
| user1 | 小红书用户1 | 普通用户 |
| user2 | 小红书用户2 | 普通用户 |

## 注意事项

1. **JWT Token**: 登录成功后返回的Token有效期为7天
2. **文件上传**: 需要配置MinIO或使用其他文件存储服务
3. **跨域配置**: 默认允许 `localhost:5173` 和 `localhost:3000` 访问
4. **逻辑删除**: 用户和笔记采用逻辑删除，数据不会物理删除
