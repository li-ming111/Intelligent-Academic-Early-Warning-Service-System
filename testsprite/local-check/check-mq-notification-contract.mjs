#!/usr/bin/env node
/**
 * 「预警通知」RabbitMQ 跨服务契约核对器（W3.1，2026-10-02）
 *
 * 这条链横跨两个服务，两边各写各的字符串，**中间没有任何编译器或运行时检查**：
 *
 *   warning-service/WarningNotifier.publish()   --发-->  notification.exchange
 *        payload = {event, userId, title, content, warningId}
 *   message-service/NotificationMessageListener.onNotification()
 *        payload.get("userId") / get("title") / get("content") / get("warningId")
 *
 * 两边对不上时的症状是**完全静默**的：生产者发得好好的、消费者 ack 得好好的、
 * 日志一片正常，只是浏览器永远收不到推送。所以这里把三件事钉住：
 *
 *   ① 消费侧读的每个键，生产侧都真的放过；
 *   ② 生产侧发的交换机 == 消费侧那个队列绑定的交换机，且**必须是 fanout**
 *      （生产者传的 routing key 是空串 `""` —— 换成 direct/topic 就一条都路由不到）；
 *   ③ `@RabbitListener` 上的队列名 == message-service 里声明的那个队列名，
 *      且那个队列确实绑在上面那个交换机上。
 *
 * 跑法：`node testsprite/local-check/check-mq-notification-contract.mjs`
 * 期望最后一行 `全部通过 ✓`，退出码 0。
 *
 * ⚠️ 为什么不用单测：这两段代码分属两个 Maven 模块，message-service 不依赖
 * warning-service（也不该为了测它而依赖）。而且本机 **RabbitMQ 5672 一直关着**，
 * 端到端跑不了 —— 静态契约核对是这台机器上唯一能真跑的判据。
 */
import fs from 'node:fs'

const ROOT = 'D:/ABS/academic-microservices'
const PRODUCER = `${ROOT}/warning-service/src/main/java/com/academic/notify/WarningNotifier.java`
const CONSUMER = `${ROOT}/message-service/src/main/java/com/academic/mq/NotificationMessageListener.java`
const CONSUMER_CFG = `${ROOT}/message-service/src/main/java/com/academic/config/RabbitMQConfig.java`

let pass = 0
const fails = []
function ok(label) { pass++ }
function bad(label, detail) { fails.push(`${label}\n      ${detail}`) }

function readOrDie(file) {
  if (!fs.existsSync(file)) {
    console.error(`✗ 源文件不存在：${file}`)
    process.exit(1)
  }
  return fs.readFileSync(file, 'utf8')
}

/** 抓出所有 `xxx.put("KEY"` / `xxx.get("KEY"` / `xxx.getOrDefault("KEY"` 里的 KEY。 */
function keysOf(text, methods) {
  const re = new RegExp(`\\.(?:${methods.join('|')})\\(\\s*"([A-Za-z0-9_]+)"`, 'g')
  const out = new Set()
  let m
  while ((m = re.exec(text)) !== null) out.add(m[1])
  return out
}

/** 取某个方法的方法体（按花括号配平），避免把别的方法里的 put/get 也算进来。 */
function methodBody(text, signature) {
  const start = text.indexOf(signature)
  if (start < 0) {
    console.error(`✗ 在源码里找不到方法签名 \`${signature}\` —— 核对器要跟着改`)
    process.exit(1)
  }
  let i = text.indexOf('{', start)
  if (i < 0) { console.error(`✗ ${signature} 后面没有 {`); process.exit(1) }
  let depth = 0
  for (let j = i; j < text.length; j++) {
    if (text[j] === '{') depth++
    else if (text[j] === '}') {
      depth--
      if (depth === 0) return text.slice(i, j + 1)
    }
  }
  console.error(`✗ ${signature} 的花括号没有配平`)
  process.exit(1)
}

// ---------- ① 生产侧的 payload 键 ⊇ 消费侧读的键 ----------

const producerText = readOrDie(PRODUCER)
const consumerText = readOrDie(CONSUMER)

const producerKeys = keysOf(methodBody(producerText, 'private void publish('), ['put'])
const consumerKeys = keysOf(methodBody(consumerText, 'public void onNotification('), ['get', 'getOrDefault'])

if (producerKeys.size === 0) {
  bad('生产侧一个 payload 键都没抓到', '是不是把 publish() 里的 payload.put 挪走了？核对器的抓取方式要跟着改')
}
if (consumerKeys.size === 0) {
  bad('消费侧一个 payload 键都没抓到', '是不是改了取值方式（比如换成强类型对象）？核对器要跟着改')
}

for (const key of consumerKeys) {
  if (producerKeys.has(key)) {
    ok(`消费侧读的键 "${key}" 生产侧确实发过`)
  } else {
    bad(`消费侧读了 "${key}"，生产侧从不发这个键`,
        `生产侧实际发的键：${[...producerKeys].sort().join(', ')} —— 这个字段会恒为 null，`
        + '而且**不会报错**，只会"推送成功但页面收不到"')
  }
}

// ---------- ② 交换机：生产侧发的 == 消费侧绑的，且必须是 fanout ----------

const producerExchange = /convertAndSend\(\s*RabbitMQConstants\.([A-Z_]+)/.exec(producerText)
if (!producerExchange) {
  bad('生产侧没有用 RabbitMQConstants 常量调 convertAndSend', '核对器抓不到交换机名了')
} else {
  const constant = producerExchange[1]

  // 消费侧配置里那个交换机的 bean：notificationExchange() { return new FanoutExchange(EXCHANGE_XXX, ...) }
  const exBean = new RegExp(`public\\s+(\\w+Exchange)\\s+notificationExchange\\(\\)\\s*\\{[^}]*new\\s+\\w+Exchange\\(\\s*RabbitMQConstants\\.([A-Z_]+)`)
  const exMatch = exBean.exec(readOrDie(CONSUMER_CFG))

  if (!exMatch) {
    bad('在 message-service 的 RabbitMQConfig 里找不到 notificationExchange() 这个 bean',
        '消费侧那个队列绑到哪个交换机上，核对器判断不了')
  } else {
    const [, exClass, consumerConstant] = exMatch

    if (constant === consumerConstant) {
      ok(`生产侧发的交换机与消费侧绑定的交换机是同一个常量（${constant}）`)
    } else {
      bad('两边用的交换机不是同一个',
          `生产侧发 ${constant}，消费侧队列绑在 ${consumerConstant} 上 —— 消息发出去没人接`)
    }

    if (exClass === 'FanoutExchange') {
      ok('交换机是 FanoutExchange —— 生产侧传的 routing key 是空串 ""，只有 fanout 会忽略它')
    } else {
      bad(`交换机是 ${exClass}，但生产侧传的 routing key 是空串 ""`,
          'direct/topic 下空串匹配不到任何绑定，消息会被丢弃（mandatory=true 时只是回一条 returned）')
    }
  }
}

// ---------- ③ 监听队列名 == 声明的队列名，且绑在上面那个交换机上 ----------

const queueConst = /public static final String QUEUE = "([^"]+)"/.exec(consumerText)
if (!queueConst) {
  bad('在 NotificationMessageListener 里找不到 `QUEUE = "..."` 常量', '核对器要跟着改')
} else {
  const queueName = queueConst[1]

  const declared = new RegExp(`public\\s+Queue\\s+(\\w+)\\(\\)\\s*\\{[^}]*QueueBuilder\\.durable\\(\\s*"${queueName.replace(/\./g, '\\.')}"`)
  const declaredMatch = declared.exec(readOrDie(CONSUMER_CFG))
  if (declaredMatch) {
    ok(`监听队列 "${queueName}" 在 message-service 里真的有声明`)

    const beanName = declaredMatch[1]
    const bound = new RegExp(`public\\s+Binding\\s+\\w+\\(\\)\\s*\\{[^}]*bind\\(\\s*${beanName}\\(\\)\\s*\\)\\s*\\.to\\(\\s*notificationExchange\\(\\)\\s*\\)`)
    if (bound.test(readOrDie(CONSUMER_CFG))) {
      ok(`"${queueName}" 确实绑在 notificationExchange() 上`)
    } else {
      bad(`"${queueName}" 没有绑在 notificationExchange() 上`,
          `它由 ${beanName}() 声明，但找不到 bind(${beanName}()).to(notificationExchange()) 的绑定 —— 队列收不到任何消息`)
    }
  } else {
    bad(`监听队列 "${queueName}" 在 message-service 的配置里没有声明`,
        '监听一个不存在的队列：broker 上缺失时靠 missingQueuesFatal(false) 不报错，于是静默收不到任何东西')
  }
}

// ---------- 汇总 ----------

if (fails.length === 0) {
  console.log(`通过 ${pass} 条，失败 0 条`)
  console.log('全部通过 ✓')
  process.exit(0)
}
console.log(`通过 ${pass} 条，失败 ${fails.length} 条`)
for (const f of fails) console.log(`  ✗ ${f}`)
process.exit(1)
