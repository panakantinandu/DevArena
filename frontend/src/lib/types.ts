// Mirrors the backend DTOs (see backend/src/main/java/**/…Dtos.java).

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type Category =
  | 'DSA' | 'JAVA' | 'SPRING' | 'DATABASES' | 'DISTRIBUTED' | 'SYSTEM_DESIGN' | 'FRONTEND' | 'BEHAVIORAL' | 'AI_CONCEPTS'

export const CATEGORY_LABEL: Record<Category, string> = {
  DSA: 'Data structures & algorithms',
  JAVA: 'Java',
  SPRING: 'Spring Boot',
  DATABASES: 'Databases',
  DISTRIBUTED: 'Distributed systems',
  SYSTEM_DESIGN: 'System design',
  FRONTEND: 'Frontend',
  BEHAVIORAL: 'Behavioral',
  AI_CONCEPTS: 'AI engineering concepts',
}

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

export interface PublicUser { id: string; username: string; displayName: string; avatarUrl: string | null }

export interface Settings {
  preferredLanguage: string; targetRole: string; prepLevel: string; dailyStudyMinutes: number
  dailyMissionMinimum: number; timeZone: string; roadmapStartDate: string; onboarded: boolean
}

export interface Me {
  id: string; email: string; username: string; displayName: string; avatarUrl: string | null; bio: string | null
  role: 'USER' | 'ADMIN'; createdAt: string; settings: Settings
}

export interface PublicProfile {
  id: string; username: string; displayName: string; avatarUrl: string | null; bio: string | null; targetRole: string
  totalXp: number; level: number; levelTitle: string; currentStreak: number
  friendship: 'SELF' | 'NONE' | 'FRIENDS' | 'REQUEST_SENT' | 'REQUEST_RECEIVED'
}

// ---- questions
export interface TopicRef { id: string; slug: string; name: string; category: Category }
export interface TopicSummary {
  id: string; slug: string; name: string; category: Category; categoryLabel: string; description: string | null
  questionCount: number; mastery: number | null
}
export interface QuestionSummary {
  id: string; slug: string; topic: TopicRef; difficulty: Difficulty; prompt: string; bookmarked: boolean
  lastAttemptCorrect: boolean | null; lastAttemptAt: string | null
}
export interface OptionView { id: string; body: string }
export interface QuestionView {
  id: string; slug: string; topic: TopicRef; difficulty: Difficulty; prompt: string; codeSnippet: string | null
  options: OptionView[]; bookmarked: boolean
}
export interface AnswerResult { correct: boolean; correctOptionId: string; explanation: string; xpAwarded: number }

// ---- missions
export interface MissionCard {
  id: string; slot: number; kind: 'QUESTION_SET' | 'INTERVIEW_STUDY' | 'DESIGN_STUDY'; category: Category
  categoryLabel: string; topicSlug: string | null; title: string; difficulty: Difficulty; estimatedMinutes: number
  xpReward: number; revision: boolean; completed: boolean; correctCount: number | null; totalCount: number | null
  completedAt: string | null
}
export interface TodayResponse {
  date: string; roadmapDay: number; phase: string; dailyMinimum: number; completedCount: number; missions: MissionCard[]
}
export interface QuestionReview {
  questionId: string; correctOptionId: string; yourOptionId: string | null; correct: boolean; explanation: string
}
export interface InterviewStudyItem {
  id: string; title: string; prompt: string; type: string; expectedConcepts: string[] | null
  answerOutline: string | null; commonMistakes: string[] | null; followUps: string[] | null
}
export interface DesignStudyItem {
  id: string; slug: string; title: string; summary: string; functionalReqs: string[]; nonFunctionalReqs: string[]
  discussionTopics: string[] | null; followUps: string[] | null
}
export interface MissionDetail {
  card: MissionCard; questions: QuestionView[] | null; review: QuestionReview[] | null
  interviewItems: InterviewStudyItem[] | null; design: DesignStudyItem | null
  yourResponse: { notes?: string; selfRating?: number; answers?: Record<string, string> } | null
}
export interface MissionSubmitResult {
  firstCompletion: boolean; xpAwarded: number; correctCount: number; totalCount: number
  unlockedAchievements: string[]; detail: MissionDetail
}

// ---- battles
export type BattleStatus = 'WAITING' | 'READY' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export interface PlayerState {
  user: PublicUser; host: boolean; ready: boolean; score: number; correctCount: number; answeredCurrent: boolean
  online: boolean
}
export interface LiveQuestion { index: number; question: QuestionView; startedAt: string; endsAt: string }
export interface PlayerRoundResult { userId: string; answered: boolean; correct: boolean; points: number }
export interface RoundReveal { index: number; correctOptionId: string; explanation: string; results: PlayerRoundResult[] }
export interface BattleState {
  id: string; roomCode: string; status: BattleStatus; host: PublicUser; category: Category; categoryLabel: string
  difficulty: Difficulty; questionCount: number; secondsPerQuestion: number; currentIndex: number
  players: PlayerState[]; live: LiveQuestion | null; reveal: RoundReveal | null; winnerId: string | null
  startedAt: string | null; endedAt: string | null
}
export interface BattleView { state: BattleState; yourAnswerForCurrent: string | null }
export interface BattleHistoryItem {
  id: string; roomCode: string; category: Category; difficulty: Difficulty; questionCount: number; status: BattleStatus
  yourScore: number; yourCorrect: number; placement: number; players: number; opponentOrWinner: PublicUser | null
  won: boolean; endedAt: string | null
}
export interface QuestionBreakdown {
  index: number; prompt: string; correctOptionId: string; correctAnswer: string; explanation: string
  results: PlayerRoundResult[]
}
export interface BattleResults { state: BattleState; questions: QuestionBreakdown[] }

// ---- interviews
export type InterviewStatus = 'CREATED' | 'LOBBY' | 'ACTIVE' | 'FEEDBACK' | 'COMPLETED' | 'CANCELLED'
export interface CodingBrief {
  slug: string; title: string; statement: string; examples: string; constraints: string; starterCode: string
}
export interface DesignBrief {
  id: string; slug: string; title: string; summary: string; functionalReqs: string[]; nonFunctionalReqs: string[]
}
export interface CandidateQuestion {
  sessionQuestionId: string; position: number; title: string; prompt: string; coding: CodingBrief | null
  design: DesignBrief | null
}
export interface SessionView {
  id: string; roomCode: string; type: string; typeLabel: string; difficulty: Difficulty; durationMinutes: number
  status: InterviewStatus; yourRole: 'INTERVIEWER' | 'CANDIDATE'; interviewer: PublicUser; candidate: PublicUser | null
  currentIndex: number; questionCount: number; startedAt: string | null; endsAt: string | null; endedAt: string | null
  current: CandidateQuestion | null; asked: CandidateQuestion[]; evaluated: boolean; online: string[]
}
export interface GuideQuestion {
  sessionQuestionId: string; position: number; title: string; prompt: string; expectedConcepts: string[]
  answerOutline: string; commonMistakes: string[]; followUps: string[]; rubricNotes: string
  solutionOutline: string | null; designDiscussionTopics: string[] | null; designFollowUps: string[] | null
  privateNote: string | null; askedAt: string | null; coding: CodingBrief | null; design: DesignBrief | null
}
export interface RubricCriterion { key: string; label: string; weight: number; description: string }
export interface InterviewerView {
  session: SessionView; questions: GuideQuestion[]; generalNote: string | null; rubric: RubricCriterion[]
}
export interface Evaluation {
  technical: number; depth: number; problemSolving: number; communication: number; followUpHandling: number
  overall: number; band: string; strengths: string; improvements: string; summary: string | null
  evaluator: PublicUser | null; createdAt: string
}
export interface InterviewReport {
  session: SessionView; evaluation: Evaluation
  questions: { position: number; title: string; prompt: string; expectedConcepts: string[] }[]
  finalCode: string; language: string; codeSubmissions: number
}
export interface InterviewHistoryItem {
  id: string; type: string; typeLabel: string; difficulty: Difficulty; status: InterviewStatus
  yourRole: 'INTERVIEWER' | 'CANDIDATE'; otherPerson: PublicUser | null; overall: number | null; createdAt: string
  endedAt: string | null
}
export interface CodeSnapshot { sessionId: string; code: string; language: string; version: number }

// ---- progress / gamification
export interface LevelInfo { level: number; title: string; xp: number; currentLevelXp: number; nextLevelXp: number | null }
export interface DayActivity { date: string; xp: number; missions: number; questions: number }
export interface SkillArea { category: Category; label: string; mastery: number | null; attempts: number }
export interface FeedItem { kind: string; user: PublicUser | null; text: string; at: string }
export interface Dashboard {
  welcome: {
    displayName: string; roadmapDay: number; phase: string; completedToday: number; dailyMinimum: number
    currentStreak: number; longestStreak: number; totalXp: number; level: LevelInfo
  }
  week: DayActivity[]; skills: SkillArea[]; friendActivity: FeedItem[]
}
export interface Streak { current: number; longest: number; todayCounted: boolean }
export interface TopicStat {
  slug: string; name: string; category: Category; attempts: number; correct: number; accuracy: number | null
  mastery: number | null
}
export interface Analytics {
  last90Days: DayActivity[]; skills: SkillArea[]; topics: TopicStat[]; mostMissed: TopicStat[]
  difficultyByWeek: { weekStart: string; easy: number | null; medium: number | null; hard: number | null }[]
  interviewScores: { sessionId: string; at: string; type: string; overall: number }[]
  battles: { played: number; won: number; avg_score: number }
  missions: { assigned: number; completed: number; minutes30: number }
  streak: Streak; estimatedStudyMinutes30d: number
}
export interface Achievement {
  code: string; name: string; description: string; icon: string; threshold: number; progress: number
  unlockedAt: string | null
}
export interface GamificationMe {
  level: LevelInfo; levels: { level: number; title: string; minXp: number }[]; streak: Streak; achievements: Achievement[]
}
export interface LeaderboardRow { rank: number; user: PublicUser; value: number; you: boolean }
export interface Leaderboard { metric: string; period: string; scope: string; rows: LeaderboardRow[] }

// ---- friends
export interface FriendsOverview {
  friends: { friendshipId: string; user: PublicUser; since: string }[]
  incoming: { friendshipId: string; user: PublicUser; sentAt: string }[]
  outgoing: { friendshipId: string; user: PublicUser; sentAt: string }[]
}

// ---- coding & design
export interface CodingProblemSummary { slug: string; title: string; topic: string; difficulty: Difficulty; submissions: number }
export interface CodingProblemDetail {
  slug: string; title: string; topic: string; difficulty: Difficulty; statement: string; examples: string
  constraints: string; starterCode: string; solutionOutline: string | null
}
export interface CodeSubmission { id: string; language: string; code: string; note: string | null; createdAt: string }
export interface DesignProblemSummary { id: string; slug: string; title: string; difficulty: Difficulty; summary: string; submitted: boolean }
export interface DesignProblemDetail extends DesignProblemSummary {
  functionalReqs: string[]; nonFunctionalReqs: string[]; discussionTopics: string[] | null; followUps: string[] | null
}
export interface DiagramNode { id: string; kind: string; label: string; x: number; y: number }
export interface DiagramEdge { id: string; from: string; to: string; label?: string }
export interface Diagram { nodes: DiagramNode[]; edges: DiagramEdge[] }
export interface DesignDocument {
  id: string; problemId: string; sessionId: string | null; diagram: Diagram; notes: string; submittedAt: string | null
  updatedAt: string; version: number
}
