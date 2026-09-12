import type { Reservation } from '@/types'

/**
 * 以“今天”为锚点生成日期（dateOffset(0) 即今天），
 * 保证 Demo 无论哪天打开，看板上都有数据。
 */
function dateOffset(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

let seq = 0
function make(r: Omit<Reservation, 'id'>): Reservation {
  seq += 1
  return { id: `mr${String(seq).padStart(4, '0')}`, ...r }
}

export const mockReservations: Reservation[] = [
  // —— 今天 ——
  // 冲突演示锚点：A101 今天 10:00 - 11:30 被占用
  make({
    title: '软件工程项目讨论',
    roomId: 'A101',
    userId: 'u2001',
    userName: '张三',
    date: dateOffset(0),
    startTime: '10:00',
    endTime: '11:30',
    participantCount: 6,
    remark: '讨论数据库设计方案',
    status: 'CONFIRMED',
  }),
  make({
    title: '项目晨会',
    roomId: 'A102',
    userId: 'u2002',
    userName: '李四',
    date: dateOffset(0),
    startTime: '09:00',
    endTime: '10:30',
    participantCount: 5,
    status: 'CONFIRMED',
  }),
  make({
    title: '课程设计讨论',
    roomId: 'A103',
    userId: 'u2003',
    userName: '王五',
    date: dateOffset(0),
    startTime: '13:00',
    endTime: '14:30',
    participantCount: 8,
    status: 'PENDING',
  }),
  make({
    title: '教研组会议',
    roomId: 'B201',
    userId: 'u2004',
    userName: '赵六',
    date: dateOffset(0),
    startTime: '15:00',
    endTime: '16:30',
    participantCount: 15,
    status: 'CONFIRMED',
  }),
  make({
    title: '小组作业讨论',
    roomId: 'A102',
    userId: 'u1001',
    userName: '李明',
    date: dateOffset(0),
    startTime: '14:00',
    endTime: '15:00',
    participantCount: 4,
    remark: '带笔记本电脑',
    status: 'CONFIRMED',
  }),

  // —— 明天 ——
  make({
    title: '毕业设计中期检查',
    roomId: 'A101',
    userId: 'u2005',
    userName: '孙七',
    date: dateOffset(1),
    startTime: '14:00',
    endTime: '15:30',
    participantCount: 6,
    status: 'CONFIRMED',
  }),
  make({
    title: '全体教职工大会',
    roomId: 'B202',
    userId: 'u2002',
    userName: '李四',
    date: dateOffset(1),
    startTime: '09:30',
    endTime: '11:30',
    participantCount: 28,
    status: 'CONFIRMED',
  }),
  make({
    title: '数据库课程设计',
    roomId: 'A103',
    userId: 'u1001',
    userName: '李明',
    date: dateOffset(1),
    startTime: '10:00',
    endTime: '11:30',
    participantCount: 6,
    status: 'PENDING',
  }),

  // —— 后天 ——
  make({
    title: '实验室安全培训',
    roomId: 'A102',
    userId: 'u2001',
    userName: '张三',
    date: dateOffset(2),
    startTime: '10:00',
    endTime: '11:00',
    participantCount: 8,
    status: 'PENDING',
  }),
  make({
    title: '研究生组会',
    roomId: 'A103',
    userId: 'u2003',
    userName: '王五',
    date: dateOffset(2),
    startTime: '16:00',
    endTime: '17:30',
    participantCount: 10,
    status: 'CONFIRMED',
  }),

  // —— 大后天 ——
  make({
    title: '青年教师座谈会',
    roomId: 'B201',
    userId: 'u2004',
    userName: '赵六',
    date: dateOffset(3),
    startTime: '08:30',
    endTime: '10:00',
    participantCount: 18,
    status: 'CONFIRMED',
  }),
  make({
    title: '学术报告：人工智能前沿',
    roomId: 'B202',
    userId: 'u2005',
    userName: '孙七',
    date: dateOffset(3),
    startTime: '14:00',
    endTime: '17:00',
    participantCount: 26,
    status: 'CONFIRMED',
  }),

  // —— 三天后 ——
  make({
    title: '教学督导会议',
    roomId: 'A101',
    userId: 'u2002',
    userName: '李四',
    date: dateOffset(4),
    startTime: '10:00',
    endTime: '11:30',
    participantCount: 5,
    status: 'CONFIRMED',
  }),

  // —— 历史数据（用于“已结束 / 已取消”展示）——
  make({
    title: '期末命题讨论',
    roomId: 'A101',
    userId: 'u1001',
    userName: '李明',
    date: dateOffset(-2),
    startTime: '09:00',
    endTime: '10:30',
    participantCount: 4,
    status: 'CONFIRMED',
  }),
  make({
    title: '临时协调会',
    roomId: 'A102',
    userId: 'u1001',
    userName: '李明',
    date: dateOffset(1),
    startTime: '16:00',
    endTime: '17:00',
    participantCount: 3,
    status: 'CANCELLED',
  }),
]
