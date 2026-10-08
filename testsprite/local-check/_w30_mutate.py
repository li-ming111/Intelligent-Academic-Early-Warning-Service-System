"""W30 打靶：证明 `/scores/by-class` 归属闸的新断言真的拦得住回归。

纪律（W28 起）：
  * 二进制读写，绝不文本模式（文本模式会把 CRLF 静默抹成 LF，W21 的教训）；
  * 每个锚点在改之前先 `assert count == 1` 自证唯一 —— 锚点没打中就等于什么都没测；
  * 改前先留 `.bak`，恢复后逐字节比对。

用法：
  python _w30_mutate.py            # 施加 M1（拆掉闸门调用）并打印锚点自检
  python _w30_mutate.py m2         # 施加 M2（闸门改成恒拒）
  python _w30_mutate.py --restore  # 从 .bak 恢复并逐字节校验
"""
import sys

SRC = r"academic-microservices\teacher-service\src\main\java\com\academic\controller\TeacherController.java"
BAK = SRC + ".bak"

# 修后的正确写法（锚点必须唯一）
GATE = ('        ApiResponse<Object> deny = denyUnlessMyClassByName(class_name, request, "只能查看本人所带班级的成绩");\r\n'
        '        if (deny != null) {\r\n'
        '            return deny;\r\n'
        '        }\r\n').encode('utf-8')
OWN = b'return classInfoMapper.countClassesNamedAndOwned(className, me) > 0 ? null : forbidden(message);'

# M2：把闸门改成恒拒 —— 用来证明「本人的班 ⇒ 200」那条放行断言是承重的
OWN_BROKEN = b'return classInfoMapper.countClassesNamedAndOwned(className, me) > 99 ? null : forbidden(message);'


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
        print("M1 applied: 闸门调用已整段移除（那个分支又变成无闸）")
    elif mode == "m2":
        assert data.count(OWN) == 1, "M2 锚点不唯一/没找到: %d" % data.count(OWN)
        open(SRC, "wb").write(data.replace(OWN, OWN_BROKEN))
        print("M2 applied: 闸门改成 count > 99（恒拒）")
    else:
        raise SystemExit("unknown mode: " + mode)


if __name__ == "__main__":
    main()
