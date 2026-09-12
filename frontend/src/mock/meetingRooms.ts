import type { MeetingRoom } from '@/types'

export const mockMeetingRooms: MeetingRoom[] = [
  {
    id: 'A101',
    name: 'A101',
    location: '第一教学楼 1F',
    capacity: 6,
    status: 'AVAILABLE',
    equipment: ['投影仪', '白板'],
  },
  {
    id: 'A102',
    name: 'A102',
    location: '第一教学楼 1F',
    capacity: 8,
    status: 'AVAILABLE',
    equipment: ['投影仪', '视频会议'],
  },
  {
    id: 'A103',
    name: 'A103',
    location: '第一教学楼 1F',
    capacity: 12,
    status: 'AVAILABLE',
    equipment: ['投影仪', '白板', '麦克风'],
  },
  {
    id: 'B201',
    name: 'B201',
    location: '第二教学楼 2F',
    capacity: 20,
    status: 'AVAILABLE',
    equipment: ['电视屏', '视频会议', '麦克风'],
  },
  {
    id: 'B202',
    name: 'B202',
    location: '第二教学楼 2F',
    capacity: 30,
    status: 'AVAILABLE',
    equipment: ['投影仪', '白板', '电视屏', '视频会议', '麦克风'],
  },
]
