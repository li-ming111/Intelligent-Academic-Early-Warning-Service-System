"""W31 打靶：证明 probe-w31 的**拒绝面**断言真的拦得住回归（不是恒绿）。

纪律（W28 起）：
  * 二进制读写，绝不文本模式（文本模式会把 CRLF 静默抹成 LF，W21 的教训）；
  * 每个锚点在改之前先 `assert count == 1` 自证唯一 —— 锚点没打中就等于什么都没测；
  * 改前先留 `.bak`，恢复后逐字节比对。

本文件只改 course-service 的 `SecurityConfig`（服务层那一道闸）。
网关那道闸的同解性由 `AuthenticationFilterTest#courseSurface_*` 单测覆盖。

用法：
  python _w31_mutate.py            # 施加 M1（拆掉 `/courses/**` 的 access 规则）
  python _w31_mutate.py --restore  # 从 .bak 恢复并逐字节校验
"""
import sys

SRC = r"academic-microservices\course-service\src\main\java\com\academic\config\SecurityConfig.java"
BAK = SRC + ".bak"

# 修后的正确写法（锚点必须唯一）。⚠️ 本仓该文件是纯 LF（core.autocrlf=true）。
GATE = (b'                .requestMatchers("/courses/**").access((authentication, context) ->\n'
        b'                        new AuthorizationDecision(isCourseManager(context.getRequest().getHeader("X-Role"))))\n')


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "m1"
    data = open(SRC, "rb").read()

    if mode == "--restore":
        bak = open(BAK, "rb").read()
        assert data != bak, "当前文件与备份一致，无需恢复（或备份是坏的）"
        open(SRC, "wb").write(bak)
        assert open(SRC, "rb").read() == bak, "恢复后逐字节比对失败"
        print("restored: %d bytes, byte-identical" % len(bak))
        return

    if not __import__("os").path.exists(BAK):
        open(BAK, "wb").write(data)

    if mode == "m1":
        assert data.count(GATE) == 1, "M1 锚点不唯一/没找到: %d" % data.count(GATE)
        open(SRC, "wb").write(data.replace(GATE, b""))
        print("M1 applied: /courses/** 的 access 规则已整段移除（回到 anyRequest().authenticated()）")
    else:
        raise SystemExit("unknown mode: " + mode)


if __name__ == "__main__":
    main()
