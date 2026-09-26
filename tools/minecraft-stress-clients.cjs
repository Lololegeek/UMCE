'use strict'

const mineflayer = require('mineflayer')

const host = process.argv[2] || '127.0.0.1'
const port = Number(process.argv[3] || 25586)
const players = Number(process.argv[4] || 100)
const loginIntervalMs = Number(process.env.UMCE_STRESS_LOGIN_INTERVAL_MS || 350)
const bots = []
let stopping = false

function startBot(index) {
  const username = `stress${String(index).padStart(3, '0')}`
  const bot = mineflayer.createBot({
    host,
    port,
    username,
    auth: 'offline',
    version: '1.21.1',
    hideErrors: true
  })
  bots.push(bot)

  bot.once('spawn', async () => {
    const yaw = (index % 4) * Math.PI / 2
    try {
      await bot.look(yaw, 0, true)
      process.stdout.write(`READY:${username}\n`)
    } catch (error) {
      process.stdout.write(`FAIL:${username}:${error.message}\n`)
    }
  })

  bot.on('kicked', (reason) => {
    if (!stopping) process.stdout.write(`FAIL:${username}:kicked:${JSON.stringify(reason)}\n`)
  })
  bot.on('error', (error) => {
    if (!stopping) process.stdout.write(`FAIL:${username}:${error.message}\n`)
  })
  bot.on('end', () => {
    if (!stopping) process.stdout.write(`FAIL:${username}:disconnected\n`)
  })
}

for (let index = 0; index < players; index++) {
  setTimeout(() => startBot(index), index * loginIntervalMs)
}

process.stdin.setEncoding('utf8')
process.stdin.on('data', (data) => {
  for (const command of data.split(/\r?\n/).map((value) => value.trim())) {
    if (command === 'start') {
      for (const bot of bots) {
        bot.setControlState('forward', true)
        bot.setControlState('sprint', true)
      }
      process.stdout.write('MOVEMENT_STARTED\n')
    } else if (command === 'stop') {
      stopping = true
      for (const bot of bots) {
        bot.clearControlStates()
        bot.quit('Stress run complete')
      }
      setTimeout(() => process.exit(0), 1500)
    }
  }
})

process.on('SIGINT', () => {
  stopping = true
  for (const bot of bots) bot.quit('Stress run interrupted')
  setTimeout(() => process.exit(130), 500)
})
