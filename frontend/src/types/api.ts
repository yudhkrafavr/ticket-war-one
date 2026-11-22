export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'COMPLETED' | 'CANCELLED'

export interface EventResponse {
  id: string
  name: string
  description: string | null
  venue: string | null
  eventDate: string
  totalTickets: number
  registrationStartAt: string | null
  registrationEndAt: string | null
  status: EventStatus
  createdAt: string
  updatedAt: string
}

export interface CreateEventRequest {
  name: string
  description: string
  venue: string
  eventDate: string
  totalTickets: number
  registrationStartAt: string | null
  registrationEndAt: string | null
}

export type UpdateEventRequest = CreateEventRequest

export type WarSessionStatus =
  | 'DRAFT'
  | 'SCHEDULED'
  | 'WAITING_ROOM_OPEN'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'

export interface WarSessionResponse {
  id: string
  eventId: string
  name: string
  description: string | null
  totalParticipants: number
  registeredParticipants: number
  totalBatches: number
  totalTickets: number
  questionsPerAttempt: number
  challengeDurationSeconds: number
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  waitingRoomOpensAt: string | null
  challengeStartsAt: string | null
  status: WarSessionStatus
  createdAt: string
  updatedAt: string
}

export interface WarSessionRequest {
  eventId: string
  name: string
  description: string
  totalParticipants: number
  totalBatches: number
  questionsPerAttempt: number
  challengeDurationSeconds: number
  waitingRoomOpensAt: string | null
  challengeStartsAt: string | null
}

export type WarBatchStatus = 'PENDING' | 'WAITING_ROOM_OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface WarBatchResponse {
  id: string
  warSessionId: string
  batchNumber: number
  participantCapacity: number
  ticketQuota: number
  status: WarBatchStatus
  createdAt: string
  updatedAt: string
}

export interface TriviaOptionResponse {
  id: string
  optionText: string
  optionOrder: number
  isCorrect: boolean
}

export interface TriviaQuestionResponse {
  id: string
  warSessionId: string
  questionText: string
  questionOrder: number
  options: TriviaOptionResponse[]
  createdAt: string
  updatedAt: string
}

export interface TriviaOptionRequest {
  optionText: string
  optionOrder: number
  isCorrect: boolean
}

export interface TriviaQuestionRequest {
  questionText: string
  questionOrder: number
  options: TriviaOptionRequest[]
}

export type WarParticipantStatus =
  | 'PREREGISTERED'
  | 'WAITING'
  | 'ASSIGNED'
  | 'CHALLENGE_IN_PROGRESS'
  | 'COMPLETED'
  | 'WINNER'
  | 'ALTERNATE'
  | 'LOST'
  | 'CANCELLED'

export interface RegisterParticipantResponse {
  participantId: string
  warSessionId: string
  email: string
  registeredAt: string
  status: WarParticipantStatus
  accessToken: string
}

export interface ParticipantProfileResponse {
  participantId: string
  warSessionId: string
  email: string
  status: WarParticipantStatus
}

export interface ParticipantAccessValidationResponse {
  valid: boolean
  participantId: string
  warSessionId: string
  email: string
  status: WarParticipantStatus
}

export interface AdminParticipantResponse {
  participantId: string
  email: string
  registeredAt: string
  queueEnteredAt: string | null
  batchNumber: number | null
  queuePosition: number | null
  status: WarParticipantStatus
}

export interface WaitingRoomResponse {
  participantId: string
  warSessionId: string
  batchId: string | null
  batchNumber: number | null
  totalBatches: number
  queuePosition: number | null
  batchParticipantCount: number
  participantCapacity: number | null
  status: WarParticipantStatus
  waitingRoomOpensAt: string | null
  challengeStartsAt: string | null
  challengeAvailable: boolean
}

export type ChallengeAttemptStatus = 'READY' | 'IN_PROGRESS' | 'SUBMITTED' | 'EXPIRED' | 'INVALID'

export interface PrepareChallengeResponse {
  attemptId: string
  participantId: string
  status: ChallengeAttemptStatus
  scheduledStartAt: string
  deadlineAt: string
  durationSeconds: number
  totalQuestions: number
  questionsPrepared: boolean
}

export interface ChallengeStartResponse {
  attemptId: string
  status: ChallengeAttemptStatus
  startedAt: string
  deadlineAt: string
  serverTime: string
  remainingSeconds: number
  totalQuestions: number
}

export interface ChallengeOptionResponse {
  id: string
  optionText: string
  optionOrder: number
}

export interface ChallengeQuestionResponse {
  attemptQuestionId: string
  questionId: string
  questionText: string
  displayOrder: number
  options: ChallengeOptionResponse[]
}

export interface ChallengeResponse {
  attemptId: string
  startedAt: string
  deadlineAt: string
  serverTime: string
  remainingSeconds: number
  status: ChallengeAttemptStatus
  questions: ChallengeQuestionResponse[]
}

export interface ChallengeSubmissionResponse {
  attemptId: string
  participantId: string
  status: ChallengeAttemptStatus
  totalQuestions: number
  correctAnswers: number
  incorrectAnswers: number
  unansweredQuestions: number
  allAnswersCorrect: boolean
  completionDurationMs: number
  submittedAt: string
  autoSubmitted: boolean
}

export interface ParticipantResultResponse {
  participantId: string
  warSessionId: string
  warSessionName: string
  leaderboardFinalizedAt: string
  batchNumber: number | null
  queuePosition: number | null
  competitionRank: number | null
  completionDurationMs: number | null
  result: WarParticipantStatus
}

export interface BookingStatusResponse {
  status: 'WAITING' | 'INVITED' | 'CLAIMED' | 'EXPIRED' | 'NOT_ELIGIBLE'
  warSessionId: string
  batchNumber: number | null
  ticketNumber: number | null
  expiresAt: string | null
  message: string
}

export interface AdminLeaderboardParticipantResponse {
  participantId: string
  email: string
  competitionRank: number | null
  queuePosition: number | null
  queueEnteredAt: string | null
  completionDurationMs: number | null
  competitionResult: WarParticipantStatus | null
}

export interface AdminBatchLeaderboardResponse {
  batchId: string
  batchNumber: number
  batchStatus: WarBatchStatus
  ticketQuota: number
  participantCapacity: number
  totalParticipants: number
  qualifiedParticipants: number
  winnerCount: number
  alternateCount: number
  lostCount: number
  participants: AdminLeaderboardParticipantResponse[]
}

export interface AdminLeaderboardResponse {
  warSessionId: string
  sessionName: string
  sessionStatus: WarSessionStatus
  leaderboardFinalized: boolean
  leaderboardFinalizedAt: string | null
  totalParticipants: number
  totalBatches: number
  totalTickets: number
  batches: AdminBatchLeaderboardResponse[]
}

export interface AdminBatchLeaderboardDetailResponse extends Omit<AdminLeaderboardResponse, 'batches'> {
  batch: AdminBatchLeaderboardResponse
}
