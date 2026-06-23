#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
小红书(Clone)后端 API 全面测试脚本
覆盖: 正向测试、负向测试、边界值测试、安全测试、数据一致性测试

使用方式: python test_api.py
"""

import requests
import json
import time
import random
import string
import sys
from dataclasses import dataclass
from typing import Optional

# ==================== 配置 ====================
BASE_URL = "http://localhost:8080/api"

# ==================== 测试结果统计 ====================
@dataclass
class TestResult:
    module: str = ""
    case_name: str = ""
    method: str = ""
    endpoint: str = ""
    expected_http: Optional[int] = None
    actual_http: Optional[int] = None
    expected_biz: Optional[int] = None
    actual_biz: Optional[int] = None
    passed: bool = False
    error_msg: str = ""

class TestReport:
    def __init__(self):
        self.results: list[TestResult] = []
        self.passed = 0
        self.failed = 0
        self.errors = []

    def add(self, result: TestResult):
        self.results.append(result)
        if result.passed:
            self.passed += 1
        else:
            self.failed += 1
            self.errors.append(result)

    def summary(self):
        total = self.passed + self.failed
        print("\n" + "=" * 80)
        print(f"测试报告汇总: 通过 {self.passed}/{total}, 失败 {self.failed}/{total}")
        print("=" * 80)
        if self.errors:
            print("\n失败用例详情:")
            print("-" * 80)
            for i, err in enumerate(self.errors, 1):
                print(f"\n  [{i}] {err.module} > {err.case_name}")
                print(f"      接口: {err.method} {err.endpoint}")
                if err.expected_http:
                    print(f"      HTTP状态码: 期望={err.expected_http}, 实际={err.actual_http}")
                if err.expected_biz is not None:
                    print(f"      业务码: 期望={err.expected_biz}, 实际={err.actual_biz}")
                if err.error_msg:
                    print(f"      说明: {err.error_msg}")
            print("-" * 80)
        return self.failed == 0

report = TestReport()
AUTH_TOKEN = {"admin": None, "testuser1": None, "testuser2": None}
CREATED_IDS = {"post": None, "comment": None, "user_test1": None, "user_test2": None}


# ==================== 工具函数 ====================
def rand_str(length=8):
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))

def rand_phone():
    return "13" + ''.join(random.choices(string.digits, k=9))

def api(method, path, json_data=None, token=None, files=None, params=None):
    """统一请求封装"""
    url = f"{BASE_URL}{path}"
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    try:
        resp = requests.request(method, url, json=json_data if not files else None,
                                headers=headers, files=files, params=params, timeout=10)
        try:
            body = resp.json()
        except:
            body = {"_raw": resp.text}
        return resp.status_code, body
    except requests.exceptions.ConnectionError:
        return -1, {"_error": "连接失败"}
    except Exception as e:
        return -2, {"_error": str(e)}

def verify(module, case_name, method, endpoint, status_code, body,
           expect_http=200, expect_biz=None, forbidden_msg=None):
    """验证结果并记录"""
    r = TestResult(module=module, case_name=case_name, method=method, endpoint=endpoint)
    r.expected_http = expect_http
    r.actual_http = status_code

    if status_code == -1:
        r.error_msg = "连接失败"
        report.add(r)
        return r

    biz_code = body.get("code") if isinstance(body, dict) else None
    r.actual_biz = biz_code
    r.expected_biz = expect_biz

    ok = True
    if expect_http and status_code != expect_http:
        ok = False
        r.error_msg = f"HTTP状态码不匹配"
    if expect_biz is not None and biz_code != expect_biz:
        ok = False
        r.error_msg = f"业务码不匹配: message={body.get('message','')}"

    # 检查响应消息中是否泄漏框架内部信息
    msg = body.get("message", "") if isinstance(body, dict) else ""
    framework_leaks = ["com.xiaohongshu", "org.springframework", "java.lang",
                       "jakarta.", "io.jsonwebtoken", "Controller", "Service",
                       "Mapper", "Exception", "at com.", "at org."]
    for leak in framework_leaks:
        if leak in msg:
            ok = False
            r.error_msg = f"响应消息泄漏框架信息: '{leak}' in message"
            break

    # 检查禁止出现的消息内容
    if forbidden_msg and ok:
        for fm in forbidden_msg:
            if fm in msg:
                ok = False
                r.error_msg = f"响应消息包含禁止内容: '{fm}'"
                break

    r.passed = ok
    report.add(r)
    return r


# ==================== 1. 用户模块测试 ====================
def test_user_module():
    print("\n" + "=" * 60)
    print("1. 用户模块测试")
    print("=" * 60)

    # --- 1.1 注册接口 ---

    # 正向: 正常注册
    uname = f"testuser_{rand_str()}"
    code, body = api("POST", "/user/register", {
        "username": uname, "password": "test123456",
        "nickname": "测试用户", "phone": rand_phone()
    })
    verify("用户模块", "1.1.1 正常注册(全部字段)", "POST", "/user/register",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        CREATED_IDS["user_test1"] = body["data"]["id"]
        print(f"  ✓ 注册成功, userId={CREATED_IDS['user_test1']}")

    # 正向: 仅必填字段
    uname2 = f"tu_{rand_str()}"
    code, body = api("POST", "/user/register", {
        "username": uname2, "password": "test123456"
    })
    verify("用户模块", "1.1.2 仅必填字段注册", "POST", "/user/register",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        CREATED_IDS["user_test2"] = body["data"]["id"]

    # 负向: 用户名已存在
    code, body = api("POST", "/user/register", {
        "username": uname, "password": "test123456"
    })
    verify("用户模块", "1.1.3 用户名已存在", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=1003)

    # 负向: 用户名为空
    code, body = api("POST", "/user/register", {
        "username": "", "password": "test123456"
    })
    verify("用户模块", "1.1.4 用户名为空", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 负向: 密码为空
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": ""
    })
    verify("用户模块", "1.1.5 密码为空", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 用户名长度=2 (低于最小值3)
    code, body = api("POST", "/user/register", {
        "username": "ab", "password": "test123456"
    })
    verify("用户模块", "1.1.6 用户名长度=2(低于最小值)", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 用户名长度=3 (最小值)
    uname3 = f"abc{rand_str()[:3]}"
    code, body = api("POST", "/user/register", {
        "username": uname3, "password": "test123456"
    })
    verify("用户模块", "1.1.7 用户名长度=3(最小值)", "POST", "/user/register",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 用户名长度=20 (最大值)
    uname4 = "a" * 17 + rand_str(3)
    code, body = api("POST", "/user/register", {
        "username": uname4, "password": "test123456"
    })
    verify("用户模块", "1.1.8 用户名长度=20(最大值)", "POST", "/user/register",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 用户名长度=21 (超过最大值)
    code, body = api("POST", "/user/register", {
        "username": "a" * 21, "password": "test123456"
    })
    verify("用户模块", "1.1.9 用户名长度=21(超过最大值)", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 密码长度=5 (低于最小值6)
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "12345"
    })
    verify("用户模块", "1.1.10 密码长度=5(低于最小值)", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 密码长度=6 (最小值)
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "123456"
    })
    verify("用户模块", "1.1.11 密码长度=6(最小值)", "POST", "/user/register",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 密码长度=20 (最大值)
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "a" * 20
    })
    verify("用户模块", "1.1.12 密码长度=20(最大值)", "POST", "/user/register",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 密码长度=21 (超过最大值)
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "a" * 21
    })
    verify("用户模块", "1.1.13 密码长度=21(超过最大值)", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 昵称长度=20 (最大值)
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "test123456",
        "nickname": "啊" * 20
    })
    verify("用户模块", "1.1.14 昵称长度=20(最大值)", "POST", "/user/register",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 昵称长度=21 (超过最大值)
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "test123456",
        "nickname": "啊" * 21
    })
    verify("用户模块", "1.1.15 昵称长度=21(超过最大值)", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 负向: 手机号格式不正确
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "test123456",
        "phone": "12345678901"
    })
    verify("用户模块", "1.1.16 手机号格式不正确", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 安全: SQL注入尝试
    code, body = api("POST", "/user/register", {
        "username": "admin' OR '1'='1", "password": "test123456"
    })
    verify("用户模块", "1.1.17 SQL注入-用户名", "POST", "/user/register",
           code, body, expect_http=400)

    # 安全: XSS尝试
    code, body = api("POST", "/user/register", {
        "username": f"tu_{rand_str()}", "password": "test123456",
        "nickname": "<script>alert('xss')</script>"
    })
    verify("用户模块", "1.1.18 XSS注入-昵称", "POST", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 安全: 请求体为空
    code, body = api("POST", "/user/register", json_data=None)
    verify("用户模块", "1.1.19 请求体为空", "POST", "/user/register",
           code, body, expect_http=400)

    # --- 1.2 登录接口 ---

    # 正向: admin登录
    code, body = api("POST", "/user/login", {
        "username": "admin", "password": "123456"
    })
    verify("用户模块", "1.2.1 正常登录(admin)", "POST", "/user/login",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        AUTH_TOKEN["admin"] = body["data"]["token"]
        print(f"  ✓ admin登录成功")

    # 正向: 新用户登录
    code, body = api("POST", "/user/login", {
        "username": uname, "password": "test123456"
    })
    verify("用户模块", "1.2.2 正常登录(新注册用户)", "POST", "/user/login",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        AUTH_TOKEN["testuser1"] = body["data"]["token"]

    # 正向: 第二个用户登录
    code, body = api("POST", "/user/login", {
        "username": uname2, "password": "test123456"
    })
    verify("用户模块", "1.2.3 正常登录(第二个用户)", "POST", "/user/login",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        AUTH_TOKEN["testuser2"] = body["data"]["token"]

    # 负向: 用户不存在
    code, body = api("POST", "/user/login", {
        "username": "nonexistent_xyz", "password": "test123456"
    })
    verify("用户模块", "1.2.4 用户不存在", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=1001)

    # 负向: 密码错误
    code, body = api("POST", "/user/login", {
        "username": "admin", "password": "wrong_password"
    })
    verify("用户模块", "1.2.5 密码错误", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=1002)

    # 负向: 用户名为空
    code, body = api("POST", "/user/login", {
        "username": "", "password": "123456"
    })
    verify("用户模块", "1.2.6 用户名为空", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=5001)

    # 负向: 密码为空
    code, body = api("POST", "/user/login", {
        "username": "admin", "password": ""
    })
    verify("用户模块", "1.2.7 密码为空", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=5001)

    # --- 1.3 获取当前用户信息 ---

    # 正向: 带有效token
    code, body = api("GET", "/user/me", token=AUTH_TOKEN.get("admin"))
    verify("用户模块", "1.3.1 获取当前用户信息(有效token)", "GET", "/user/me",
           code, body, expect_http=200, expect_biz=200)

    # 安全: 不带token
    code, body = api("GET", "/user/me")
    verify("用户模块", "1.3.2 获取当前用户信息(无token)", "GET", "/user/me",
           code, body, expect_http=401, expect_biz=1005)

    # 安全: 伪造token
    code, body = api("GET", "/user/me", token="fake.invalid.token")
    verify("用户模块", "1.3.3 获取当前用户信息(伪造token)", "GET", "/user/me",
           code, body, expect_http=401, expect_biz=1005)

    # --- 1.4 根据ID获取用户信息 ---

    # 正向: 有效ID
    code, body = api("GET", "/user/1")
    verify("用户模块", "1.4.1 根据ID获取用户(有效ID)", "GET", "/user/1",
           code, body, expect_http=200, expect_biz=200)

    # 负向: 不存在的ID
    code, body = api("GET", "/user/999999")
    verify("用户模块", "1.4.2 根据ID获取用户(不存在的ID)", "GET", "/user/999999",
           code, body, expect_http=400, expect_biz=1001)

    # 负向: 非数字ID
    code, body = api("GET", "/user/abc")
    verify("用户模块", "1.4.3 根据ID获取用户(非数字ID)", "GET", "/user/abc",
           code, body, expect_http=400)

    # --- 1.5 更新用户信息 ---

    # 正向: 更新昵称
    code, body = api("PUT", "/user/update",
                     {"nickname": "新昵称测试"}, token=AUTH_TOKEN.get("testuser1"))
    verify("用户模块", "1.5.1 更新昵称", "PUT", "/user/update",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 更新多个字段
    code, body = api("PUT", "/user/update", {
        "nickname": "多字段更新", "gender": 1, "bio": "新的个人简介"
    }, token=AUTH_TOKEN.get("testuser1"))
    verify("用户模块", "1.5.2 更新多个字段", "PUT", "/user/update",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 昵称长度=20
    code, body = api("PUT", "/user/update",
                     {"nickname": "啊" * 20}, token=AUTH_TOKEN.get("testuser1"))
    verify("用户模块", "1.5.3 昵称长度=20(最大值)", "PUT", "/user/update",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 昵称长度=21
    code, body = api("PUT", "/user/update",
                     {"nickname": "啊" * 21}, token=AUTH_TOKEN.get("testuser1"))
    verify("用户模块", "1.5.4 昵称长度=21(超过最大值)", "PUT", "/user/update",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 个人简介长度=200
    code, body = api("PUT", "/user/update",
                     {"bio": "a" * 200}, token=AUTH_TOKEN.get("testuser1"))
    verify("用户模块", "1.5.5 个人简介长度=200(最大值)", "PUT", "/user/update",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 个人简介长度=201
    code, body = api("PUT", "/user/update",
                     {"bio": "a" * 201}, token=AUTH_TOKEN.get("testuser1"))
    verify("用户模块", "1.5.6 个人简介长度=201(超过最大值)", "PUT", "/user/update",
           code, body, expect_http=400, expect_biz=5001)

    # 安全: 无token更新
    code, body = api("PUT", "/user/update", {"nickname": "黑客"})
    verify("用户模块", "1.5.7 无token更新", "PUT", "/user/update",
           code, body, expect_http=401, expect_biz=1005)


# ==================== 2. 笔记模块测试 ====================
def test_post_module():
    print("\n" + "=" * 60)
    print("2. 笔记模块测试")
    print("=" * 60)
    token = AUTH_TOKEN.get("testuser1")
    admin_token = AUTH_TOKEN.get("admin")

    # --- 2.1 创建笔记 ---

    # 正向: 正常创建
    code, body = api("POST", "/post/create", {
        "title": "测试笔记标题",
        "content": "这是一篇测试笔记的内容",
        "type": 0
    }, token=token)
    verify("笔记模块", "2.1.1 正常创建笔记", "POST", "/post/create",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        CREATED_IDS["post"] = body["data"]["id"]
        print(f"  ✓ 创建笔记成功, postId={CREATED_IDS['post']}")

    # 正向: 带图片URL
    code, body = api("POST", "/post/create", {
        "title": "带图片的笔记",
        "content": "包含图片",
        "imageUrls": ["https://example.com/img1.jpg"]
    }, token=token)
    verify("笔记模块", "2.1.2 带图片URL创建", "POST", "/post/create",
           code, body, expect_http=200, expect_biz=200)

    # 负向: 标题为空
    code, body = api("POST", "/post/create", {
        "title": "", "content": "内容"
    }, token=token)
    verify("笔记模块", "2.1.3 标题为空", "POST", "/post/create",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 标题长度=200
    code, body = api("POST", "/post/create", {
        "title": "a" * 200, "content": "内容"
    }, token=token)
    verify("笔记模块", "2.1.4 标题长度=200(最大值)", "POST", "/post/create",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 标题长度=201
    code, body = api("POST", "/post/create", {
        "title": "a" * 201, "content": "内容"
    }, token=token)
    verify("笔记模块", "2.1.5 标题长度=201(超过最大值)", "POST", "/post/create",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 正文内容长度=10000
    code, body = api("POST", "/post/create", {
        "title": "边界值测试", "content": "a" * 10000
    }, token=token)
    verify("笔记模块", "2.1.6 正文内容长度=10000(最大值)", "POST", "/post/create",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 正文内容长度=10001
    code, body = api("POST", "/post/create", {
        "title": "边界值测试", "content": "a" * 10001
    }, token=token)
    verify("笔记模块", "2.1.7 正文内容长度=10001(超过最大值)", "POST", "/post/create",
           code, body, expect_http=400, expect_biz=5001)

    # 安全: 无token创建
    code, body = api("POST", "/post/create", {
        "title": "无token", "content": "内容"
    })
    verify("笔记模块", "2.1.8 无token创建", "POST", "/post/create",
           code, body, expect_http=401, expect_biz=1005)

    # --- 2.2 更新笔记 ---

    post_id = CREATED_IDS.get("post")
    if post_id:
        # 正向: 更新标题
        code, body = api("PUT", "/post/update", {
            "id": post_id, "title": "更新后的标题"
        }, token=token)
        verify("笔记模块", "2.2.1 更新笔记标题", "PUT", "/post/update",
               code, body, expect_http=200, expect_biz=200)

        # 负向: 非作者更新
        code, body = api("PUT", "/post/update", {
            "id": post_id, "title": "非作者尝试更新"
        }, token=admin_token)
        verify("笔记模块", "2.2.2 非作者更新", "PUT", "/post/update",
               code, body, expect_http=400, expect_biz=2003)

        # 负向: 不存在的笔记ID
        code, body = api("PUT", "/post/update", {
            "id": 999999, "title": "更新不存在的笔记"
        }, token=token)
        verify("笔记模块", "2.2.3 更新不存在的笔记", "PUT", "/post/update",
               code, body, expect_http=400, expect_biz=2001)

    # --- 2.3 删除笔记 ---

    # 先创建一个用于删除的笔记
    code, body = api("POST", "/post/create", {
        "title": "待删除的笔记", "content": "将被删除"
    }, token=token)
    del_post_id = body.get("data", {}).get("id") if body.get("code") == 200 else None

    if del_post_id:
        # 正向: 作者删除
        code, body = api("DELETE", f"/post/delete/{del_post_id}", token=token)
        verify("笔记模块", "2.3.1 作者删除笔记", "DELETE", f"/post/delete/{del_post_id}",
               code, body, expect_http=200, expect_biz=200)

    # 安全: 无token删除
    code, body = api("DELETE", "/post/delete/1")
    verify("笔记模块", "2.3.2 无token删除", "DELETE", "/post/delete/1",
           code, body, expect_http=401, expect_biz=1005)

    # --- 2.4 获取笔记详情 ---

    if post_id:
        # 正向: 获取详情
        code, body = api("GET", f"/post/{post_id}")
        verify("笔记模块", "2.4.1 获取笔记详情", "GET", f"/post/{post_id}",
               code, body, expect_http=200, expect_biz=200)

        # 验证返回字段完整性
        if body.get("code") == 200:
            data = body["data"]
            required_fields = ["id", "userId", "title", "content", "type",
                               "viewCount", "likeCount", "commentCount", "collectCount",
                               "status", "createTime"]
            missing = [f for f in required_fields if f not in data]
            if missing:
                verify("笔记模块", "2.4.2 返回字段完整性", "GET", f"/post/{post_id}",
                       code, body, expect_http=200)
                report.results[-1].passed = False
                report.results[-1].error_msg = f"缺失字段: {missing}"
                report.failed += 1
                report.passed -= 1
            else:
                verify("笔记模块", "2.4.2 返回字段完整性", "GET", f"/post/{post_id}",
                       code, body, expect_http=200, expect_biz=200)

    # 负向: 不存在的笔记
    code, body = api("GET", "/post/999999")
    verify("笔记模块", "2.4.3 获取不存在的笔记", "GET", "/post/999999",
           code, body, expect_http=400, expect_biz=2001)

    # --- 2.5 笔记列表 ---

    # 正向: 默认分页
    code, body = api("GET", "/post/list")
    verify("笔记模块", "2.5.1 默认分页列表", "GET", "/post/list",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 带分页参数
    code, body = api("GET", "/post/list", params={"pageNum": 1, "pageSize": 5})
    verify("笔记模块", "2.5.2 带分页参数", "GET", "/post/list",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 关键词搜索
    code, body = api("GET", "/post/list", params={"keyword": "测试"})
    verify("笔记模块", "2.5.3 关键词搜索", "GET", "/post/list",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 类型筛选
    code, body = api("GET", "/post/list", params={"type": 0})
    verify("笔记模块", "2.5.4 类型筛选(type=0)", "GET", "/post/list",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 热门排序
    code, body = api("GET", "/post/list", params={"sortType": "hot"})
    verify("笔记模块", "2.5.5 热门排序", "GET", "/post/list",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: pageSize=0
    code, body = api("GET", "/post/list", params={"pageSize": 0})
    verify("笔记模块", "2.5.6 pageSize=0", "GET", "/post/list",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: pageSize=101 (超过最大值100)
    code, body = api("GET", "/post/list", params={"pageSize": 101})
    verify("笔记模块", "2.5.7 pageSize=101(超过最大值)", "GET", "/post/list",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: pageNum=0
    code, body = api("GET", "/post/list", params={"pageNum": 0})
    verify("笔记模块", "2.5.8 pageNum=0", "GET", "/post/list",
           code, body, expect_http=400, expect_biz=5001)

    # 负向: 负数页码
    code, body = api("GET", "/post/list", params={"pageNum": -1})
    verify("笔记模块", "2.5.9 负数页码", "GET", "/post/list",
           code, body, expect_http=400, expect_biz=5001)

    # --- 2.6 用户笔记列表 ---
    code, body = api("GET", "/post/user/1", params={"pageNum": 1, "pageSize": 5})
    verify("笔记模块", "2.6.1 获取用户笔记列表", "GET", "/post/user/1",
           code, body, expect_http=200, expect_biz=200)

    # --- 2.7 我的笔记列表 ---
    code, body = api("GET", "/post/my", token=token)
    verify("笔记模块", "2.7.1 获取我的笔记列表", "GET", "/post/my",
           code, body, expect_http=200, expect_biz=200)

    # 安全: 无token
    code, body = api("GET", "/post/my")
    verify("笔记模块", "2.7.2 无token获取我的笔记", "GET", "/post/my",
           code, body, expect_http=401, expect_biz=1005)


# ==================== 3. 评论模块测试 ====================
def test_comment_module():
    print("\n" + "=" * 60)
    print("3. 评论模块测试")
    print("=" * 60)
    token = AUTH_TOKEN.get("testuser1")
    token2 = AUTH_TOKEN.get("testuser2")
    post_id = CREATED_IDS.get("post")

    if not post_id:
        print("  ⚠ 跳过评论测试: 无可用笔记ID")
        return

    # --- 3.1 发表评论 ---

    # 正向: 发表一级评论
    code, body = api("POST", "/comment/create", {
        "postId": post_id, "content": "这是一条测试评论"
    }, token=token)
    verify("评论模块", "3.1.1 发表一级评论", "POST", "/comment/create",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        CREATED_IDS["comment"] = body["data"]["id"]
        print(f"  ✓ 发表评论成功, commentId={CREATED_IDS['comment']}")

    comment_id = CREATED_IDS.get("comment")

    # 正向: 发表回复评论
    if comment_id:
        code, body = api("POST", "/comment/create", {
            "postId": post_id, "content": "这是一条回复",
            "parentId": comment_id, "replyUserId": CREATED_IDS.get("user_test1", 1)
        }, token=token2)
        verify("评论模块", "3.1.2 发表回复评论", "POST", "/comment/create",
               code, body, expect_http=200, expect_biz=200)

    # 负向: 笔记ID为空
    code, body = api("POST", "/comment/create", {
        "content": "评论内容"
    }, token=token)
    verify("评论模块", "3.1.3 笔记ID为空", "POST", "/comment/create",
           code, body, expect_http=400, expect_biz=5001)

    # 负向: 评论内容为空
    code, body = api("POST", "/comment/create", {
        "postId": post_id, "content": ""
    }, token=token)
    verify("评论模块", "3.1.4 评论内容为空", "POST", "/comment/create",
           code, body, expect_http=400, expect_biz=5001)

    # 边界值: 评论内容长度=500
    code, body = api("POST", "/comment/create", {
        "postId": post_id, "content": "a" * 500
    }, token=token)
    verify("评论模块", "3.1.5 评论内容长度=500(最大值)", "POST", "/comment/create",
           code, body, expect_http=200, expect_biz=200)

    # 边界值: 评论内容长度=501
    code, body = api("POST", "/comment/create", {
        "postId": post_id, "content": "a" * 501
    }, token=token)
    verify("评论模块", "3.1.6 评论内容长度=501(超过最大值)", "POST", "/comment/create",
           code, body, expect_http=400, expect_biz=5001)

    # 安全: 无token
    code, body = api("POST", "/comment/create", {
        "postId": post_id, "content": "无token评论"
    })
    verify("评论模块", "3.1.7 无token发表评论", "POST", "/comment/create",
           code, body, expect_http=401, expect_biz=1005)

    # 负向: 不存在的笔记ID
    code, body = api("POST", "/comment/create", {
        "postId": 999999, "content": "评论不存在的笔记"
    }, token=token)
    verify("评论模块", "3.1.8 评论不存在的笔记", "POST", "/comment/create",
           code, body, expect_http=400, expect_biz=2001)

    # --- 3.2 删除评论 ---

    # 正向: 作者删除
    if comment_id:
        code, body = api("DELETE", f"/comment/delete/{comment_id}", token=token)
        verify("评论模块", "3.2.1 作者删除评论", "DELETE", f"/comment/delete/{comment_id}",
               code, body, expect_http=200, expect_biz=200)

    # 负向: 非作者删除
    code, body = api("POST", "/comment/create", {
        "postId": post_id, "content": "将被他人尝试删除"
    }, token=token)
    if body.get("code") == 200:
        cid = body["data"]["id"]
        code, body = api("DELETE", f"/comment/delete/{cid}", token=token2)
        verify("评论模块", "3.2.2 非作者删除评论", "DELETE", f"/comment/delete/{cid}",
               code, body, expect_http=400, expect_biz=2003)

    # --- 3.3 获取笔记评论列表 ---
    code, body = api("GET", f"/comment/post/{post_id}", params={"pageNum": 1, "pageSize": 10})
    verify("评论模块", "3.3.1 获取笔记评论列表", "GET", f"/comment/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)

    # --- 3.4 获取评论回复列表 ---
    if comment_id:
        # 重新创建评论用于测试
        code, body = api("POST", "/comment/create", {
            "postId": post_id, "content": "父评论"
        }, token=token)
        if body.get("code") == 200:
            parent_id = body["data"]["id"]
            code, body = api("POST", "/comment/create", {
                "postId": post_id, "content": "子评论",
                "parentId": parent_id, "replyUserId": 1
            }, token=token2)
            code, body = api("GET", f"/comment/replies/{parent_id}",
                             params={"pageNum": 1, "pageSize": 10})
            verify("评论模块", "3.4.1 获取评论回复列表", "GET", f"/comment/replies/{parent_id}",
                   code, body, expect_http=200, expect_biz=200)


# ==================== 4. 点赞模块测试 ====================
def test_like_module():
    print("\n" + "=" * 60)
    print("4. 点赞模块测试")
    print("=" * 60)
    token = AUTH_TOKEN.get("testuser1")
    post_id = CREATED_IDS.get("post")

    if not post_id:
        print("  ⚠ 跳过点赞测试: 无可用笔记ID")
        return

    # --- 4.1 点赞/取消点赞笔记 ---

    # 正向: 点赞
    code, body = api("POST", f"/like/post/{post_id}", token=token)
    verify("点赞模块", "4.1.1 点赞笔记", "POST", f"/like/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        print(f"  ✓ 点赞结果: liked={body['data']['liked']}")

    # 正向: 取消点赞(toggle)
    code, body = api("POST", f"/like/post/{post_id}", token=token)
    verify("点赞模块", "4.1.2 取消点赞笔记(toggle)", "POST", f"/like/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 再次点赞
    code, body = api("POST", f"/like/post/{post_id}", token=token)
    verify("点赞模块", "4.1.3 再次点赞", "POST", f"/like/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)

    # 负向: 不存在的笔记
    code, body = api("POST", "/like/post/999999", token=token)
    verify("点赞模块", "4.1.4 点赞不存在的笔记", "POST", "/like/post/999999",
           code, body, expect_http=400, expect_biz=2001)

    # 安全: 无token
    code, body = api("POST", f"/like/post/{post_id}")
    verify("点赞模块", "4.1.5 无token点赞", "POST", f"/like/post/{post_id}",
           code, body, expect_http=401, expect_biz=1005)

    # --- 4.2 点赞评论 ---
    # 创建一条评论用于点赞测试
    code, body = api("POST", "/comment/create", {
        "postId": post_id, "content": "用于点赞测试的评论"
    }, token=token)
    if body.get("code") == 200:
        comment_id = body["data"]["id"]
        code, body = api("POST", f"/like/comment/{comment_id}", token=token)
        verify("点赞模块", "4.2.1 点赞评论", "POST", f"/like/comment/{comment_id}",
               code, body, expect_http=200, expect_biz=200)

        # 取消点赞评论
        code, body = api("POST", f"/like/comment/{comment_id}", token=token)
        verify("点赞模块", "4.2.2 取消点赞评论", "POST", f"/like/comment/{comment_id}",
               code, body, expect_http=200, expect_biz=200)

    # --- 4.3 获取笔记点赞状态 ---
    code, body = api("GET", f"/like/status/post/{post_id}", token=token)
    verify("点赞模块", "4.3.1 获取笔记点赞状态", "GET", f"/like/status/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)


# ==================== 5. 收藏模块测试 ====================
def test_collect_module():
    print("\n" + "=" * 60)
    print("5. 收藏模块测试")
    print("=" * 60)
    token = AUTH_TOKEN.get("testuser1")
    post_id = CREATED_IDS.get("post")

    if not post_id:
        print("  ⚠ 跳过收藏测试: 无可用笔记ID")
        return

    # 正向: 收藏
    code, body = api("POST", f"/collect/post/{post_id}", token=token)
    verify("收藏模块", "5.1.1 收藏笔记", "POST", f"/collect/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        print(f"  ✓ 收藏结果: collected={body['data']['collected']}")

    # 正向: 取消收藏(toggle)
    code, body = api("POST", f"/collect/post/{post_id}", token=token)
    verify("收藏模块", "5.1.2 取消收藏(toggle)", "POST", f"/collect/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 再次收藏
    code, body = api("POST", f"/collect/post/{post_id}", token=token)
    verify("收藏模块", "5.1.3 再次收藏", "POST", f"/collect/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)

    # 负向: 不存在的笔记
    code, body = api("POST", "/collect/post/999999", token=token)
    verify("收藏模块", "5.1.4 收藏不存在的笔记", "POST", "/collect/post/999999",
           code, body, expect_http=400, expect_biz=2001)

    # 安全: 无token
    code, body = api("POST", f"/collect/post/{post_id}")
    verify("收藏模块", "5.1.5 无token收藏", "POST", f"/collect/post/{post_id}",
           code, body, expect_http=401, expect_biz=1005)

    # --- 5.2 获取收藏状态 ---
    code, body = api("GET", f"/collect/status/post/{post_id}", token=token)
    verify("收藏模块", "5.2.1 获取收藏状态", "GET", f"/collect/status/post/{post_id}",
           code, body, expect_http=200, expect_biz=200)


# ==================== 6. 关注模块测试 ====================
def test_follow_module():
    print("\n" + "=" * 60)
    print("6. 关注模块测试")
    print("=" * 60)
    token1 = AUTH_TOKEN.get("testuser1")
    token2 = AUTH_TOKEN.get("testuser2")

    if not token1 or not token2:
        print("  ⚠ 跳过关注测试: 需要至少两个用户的token")
        return

    # 获取用户ID
    _, me1 = api("GET", "/user/me", token=token1)
    _, me2 = api("GET", "/user/me", token=token2)
    uid1 = me1.get("data", {}).get("id")
    uid2 = me2.get("data", {}).get("id")

    if not uid1 or not uid2:
        print("  ⚠ 跳过关注测试: 无法获取用户ID")
        return

    print(f"  用户1 ID={uid1}, 用户2 ID={uid2}")

    # --- 6.1 关注/取消关注 ---

    # 正向: 关注
    code, body = api("POST", f"/follow/{uid2}", token=token1)
    verify("关注模块", "6.1.1 关注用户", "POST", f"/follow/{uid2}",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        print(f"  ✓ 关注结果: followed={body['data']['followed']}")

    # 正向: 取消关注(toggle)
    code, body = api("POST", f"/follow/{uid2}", token=token1)
    verify("关注模块", "6.1.2 取消关注(toggle)", "POST", f"/follow/{uid2}",
           code, body, expect_http=200, expect_biz=200)

    # 正向: 再次关注
    code, body = api("POST", f"/follow/{uid2}", token=token1)
    verify("关注模块", "6.1.3 再次关注", "POST", f"/follow/{uid2}",
           code, body, expect_http=200, expect_biz=200)

    # 负向: 关注自己
    code, body = api("POST", f"/follow/{uid1}", token=token1)
    verify("关注模块", "6.1.4 关注自己", "POST", f"/follow/{uid1}",
           code, body, expect_http=400, expect_biz=6003)

    # 安全: 无token
    code, body = api("POST", f"/follow/{uid2}")
    verify("关注模块", "6.1.5 无token关注", "POST", f"/follow/{uid2}",
           code, body, expect_http=401, expect_biz=1005)

    # 负向: 关注不存在的用户
    code, body = api("POST", "/follow/999999", token=token1)
    verify("关注模块", "6.1.6 关注不存在的用户", "POST", "/follow/999999",
           code, body, expect_http=400, expect_biz=1001)

    # --- 6.2 获取关注状态 ---
    code, body = api("GET", f"/follow/status/{uid2}", token=token1)
    verify("关注模块", "6.2.1 获取关注状态", "GET", f"/follow/status/{uid2}",
           code, body, expect_http=200, expect_biz=200)

    # --- 6.3 获取关注列表 ---
    code, body = api("GET", f"/follow/following/{uid1}", params={"pageNum": 1, "pageSize": 10})
    verify("关注模块", "6.3.1 获取关注列表", "GET", f"/follow/following/{uid1}",
           code, body, expect_http=200, expect_biz=200)

    # --- 6.4 获取粉丝列表 ---
    code, body = api("GET", f"/follow/followers/{uid2}", params={"pageNum": 1, "pageSize": 10})
    verify("关注模块", "6.4.1 获取粉丝列表", "GET", f"/follow/followers/{uid2}",
           code, body, expect_http=200, expect_biz=200)

    # --- 6.5 获取关注/粉丝数量 ---
    code, body = api("GET", f"/follow/count/{uid1}")
    verify("关注模块", "6.5.1 获取关注粉丝数量", "GET", f"/follow/count/{uid1}",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        data = body["data"]
        print(f"  ✓ 关注数={data.get('followingCount')}, 粉丝数={data.get('followersCount')}")


# ==================== 7. 文件上传模块测试 ====================
def test_file_module():
    print("\n" + "=" * 60)
    print("7. 文件上传模块测试")
    print("=" * 60)
    token = AUTH_TOKEN.get("testuser1")

    import io
    # 最小PNG文件
    png_data = (
        b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01'
        b'\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00'
        b'\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00'
        b'\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82'
    )

    # 正向: 上传图片
    files = {"file": ("test.png", io.BytesIO(png_data), "image/png")}
    code, body = api("POST", "/upload/image", files=files, token=token)
    verify("文件上传模块", "7.1.1 上传图片", "POST", "/upload/image",
           code, body, expect_http=200, expect_biz=200)
    if body.get("code") == 200:
        print(f"  ✓ 上传成功, url={body['data'].get('url')}")

    # 正向: 上传通用文件
    files = {"file": ("test.txt", io.BytesIO(b"hello world"), "text/plain")}
    code, body = api("POST", "/upload/file", files=files, token=token)
    verify("文件上传模块", "7.1.2 上传通用文件", "POST", "/upload/file",
           code, body, expect_http=200, expect_biz=200)

    # 安全: 无token上传
    files = {"file": ("test.png", io.BytesIO(png_data), "image/png")}
    code, body = api("POST", "/upload/image", files=files)
    verify("文件上传模块", "7.1.3 无token上传", "POST", "/upload/image",
           code, body, expect_http=401, expect_biz=1005)

    # 负向: 无文件
    code, body = api("POST", "/upload/image", token=token)
    verify("文件上传模块", "7.1.4 无文件上传", "POST", "/upload/image",
           code, body, expect_http=400)


# ==================== 8. 安全测试 ====================
def test_security():
    print("\n" + "=" * 60)
    print("8. 安全测试")
    print("=" * 60)

    # SQL注入
    code, body = api("POST", "/user/login", {
        "username": "admin'--", "password": "anything"
    })
    verify("安全测试", "8.1.1 SQL注入-用户名登录", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=1001)

    code, body = api("GET", "/post/list", params={"keyword": "'; DROP TABLE post; --"})
    verify("安全测试", "8.1.2 SQL注入-搜索关键词", "GET", "/post/list",
           code, body, expect_http=200, expect_biz=200)

    # 请求体异常
    code, body = api("POST", "/user/login", json_data=None)
    verify("安全测试", "8.2.1 空Body请求", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=5001)

    code, body = api("POST", "/user/login", json_data={})
    verify("安全测试", "8.2.2 空JSON对象", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=5001)

    # 无效JSON (用原始字符串发送)
    url = f"{BASE_URL}/user/login"
    try:
        resp = requests.post(url, data="not json at all",
                             headers={"Content-Type": "application/json"}, timeout=10)
        code, body = resp.status_code, resp.json()
    except:
        code, body = -1, {}
    verify("安全测试", "8.2.3 无效JSON格式", "POST", "/user/login",
           code, body, expect_http=400, expect_biz=5001)

    # 错误HTTP方法
    code, body = api("DELETE", "/user/login")
    verify("安全测试", "8.2.4 错误HTTP方法(DELETE到POST接口)", "DELETE", "/user/login",
           code, body, expect_http=405, expect_biz=5001)

    code, body = api("GET", "/user/register")
    verify("安全测试", "8.2.5 错误HTTP方法(GET到POST接口)", "GET", "/user/register",
           code, body, expect_http=400, expect_biz=5001)

    # 未授权访问
    protected = [
        ("GET", "/user/me"),
        ("PUT", "/user/update"),
        ("POST", "/post/create"),
        ("GET", "/post/my"),
        ("POST", "/comment/create"),
        ("POST", "/like/post/1"),
        ("POST", "/collect/post/1"),
        ("POST", "/follow/1"),
    ]
    for method, path in protected:
        code, body = api(method, path, json_data={} if method in ["POST", "PUT"] else None)
        verify("安全测试", f"8.3.1 未授权访问 {method} {path}", method, path,
               code, body, expect_http=401, expect_biz=1005)

    # 不存在的路由 (Spring Security拦截先于路由，返回401)
    code, body = api("GET", "/nonexistent/endpoint")
    verify("安全测试", "8.4.1 不存在的路由", "GET", "/nonexistent/endpoint",
           code, body, expect_http=401)


# ==================== 9. 数据一致性测试 ====================
def test_consistency():
    print("\n" + "=" * 60)
    print("9. 数据一致性测试")
    print("=" * 60)
    token = AUTH_TOKEN.get("testuser1")
    post_id = CREATED_IDS.get("post")

    if not post_id:
        print("  ⚠ 跳过一致性测试")
        return

    # 先确保从"未点赞"状态开始
    api("POST", f"/like/post/{post_id}", token=token)  # 可能取消也可能点赞

    # 连续点赞测试(toggle一致性)
    results = []
    for i in range(6):
        code, body = api("POST", f"/like/post/{post_id}", token=token)
        if body.get("code") == 200:
            results.append(body["data"]["liked"])

    expected_pattern = [True, False, True, False, True, False]
    if results == expected_pattern:
        verify("一致性测试", "9.1 连续6次点赞toggle一致性", "POST", f"/like/post/{post_id}",
               code, body, expect_http=200, expect_biz=200)
        print(f"  ✓ toggle模式正确: {results}")
    else:
        verify("一致性测试", "9.1 连续6次点赞toggle一致性", "POST", f"/like/post/{post_id}",
               code, body, expect_http=200, expect_biz=200)
        report.results[-1].passed = False
        report.results[-1].error_msg = f"toggle模式异常: {results}"
        report.failed += 1
        report.passed -= 1

    # 浏览量递增验证
    _, b1 = api("GET", f"/post/{post_id}")
    _, b2 = api("GET", f"/post/{post_id}")
    if b1.get("code") == 200 and b2.get("code") == 200:
        v1 = b1["data"]["viewCount"]
        v2 = b2["data"]["viewCount"]
        if v2 > v1:
            verify("一致性测试", "9.2 浏览量递增", "GET", f"/post/{post_id}",
                   200, b2, expect_http=200, expect_biz=200)
            print(f"  ✓ 浏览量递增正确: {v1} -> {v2}")
        else:
            verify("一致性测试", "9.2 浏览量递增", "GET", f"/post/{post_id}",
                   200, b2, expect_http=200, expect_biz=200)
            report.results[-1].passed = False
            report.results[-1].error_msg = f"浏览量未递增: {v1} -> {v2}"
            report.failed += 1
            report.passed -= 1


# ==================== 主函数 ====================
def main():
    print("=" * 80)
    print("小红书(Clone)后端 API 全面测试")
    print(f"目标地址: {BASE_URL}")
    print(f"测试时间: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 80)

    # 检查服务是否可用
    try:
        resp = requests.get(f"{BASE_URL}/user/1", timeout=5)
        print(f"\n✓ 服务可用, 状态码={resp.status_code}")
    except requests.exceptions.ConnectionError:
        print(f"\n✗ 无法连接到 {BASE_URL}, 请确认服务已启动!")
        sys.exit(1)

    # 执行所有测试模块
    test_user_module()
    test_post_module()
    test_comment_module()
    test_like_module()
    test_collect_module()
    test_follow_module()
    test_file_module()
    test_security()
    test_consistency()

    # 输出汇总
    all_pass = report.summary()

    # 保存详细报告到文件
    report_path = "test_report.txt"
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(f"小红书 API 测试报告\n")
        f.write(f"生成时间: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"目标地址: {BASE_URL}\n")
        f.write(f"{'='*80}\n\n")
        f.write(f"总计: {report.passed + report.failed} | 通过: {report.passed} | 失败: {report.failed}\n\n")

        if report.errors:
            f.write("失败用例:\n")
            f.write("-" * 80 + "\n")
            for err in report.errors:
                f.write(f"\n[{err.module}] {err.case_name}\n")
                f.write(f"  接口: {err.method} {err.endpoint}\n")
                f.write(f"  HTTP: 期望={err.expected_http} 实际={err.actual_http}\n")
                f.write(f"  业务码: 期望={err.expected_biz} 实际={err.actual_biz}\n")
                f.write(f"  说明: {err.error_msg}\n")

        f.write(f"\n{'='*80}\n")
        f.write("全部用例明细:\n")
        f.write("-" * 80 + "\n")
        for r in report.results:
            status = "PASS" if r.passed else "FAIL"
            f.write(f"[{status}] {r.module} > {r.case_name} ({r.method} {r.endpoint})\n")
            if not r.passed:
                f.write(f"       原因: {r.error_msg}\n")

    print(f"\n详细报告已保存至: {report_path}")
    return 0 if all_pass else 1


if __name__ == "__main__":
    sys.exit(main())
