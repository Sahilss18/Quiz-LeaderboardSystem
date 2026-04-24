# Quiz Leaderboard System

A Java-based backend application that processes quiz data from an external API, handles duplicate responses, and generates a correct leaderboard.

---

## Problem Statement

The system simulates a real-world backend integration scenario where:

- Data is fetched from an external API in multiple polls
- Duplicate responses may occur
- The system must ensure idempotent processing
- Final output should be a correct leaderboard with total scores

---

## Features

- Polls API 10 times (poll = 0 -> 9)
- Maintains 5-second delay between requests
- Handles duplicate events using Set
- Aggregates scores using HashMap
- Generates sorted leaderboard (descending order)
- Submits result to API
- Ensures idempotency

---

## Key Concept

### Deduplication Logic

Each event is uniquely identified using:

```text
(roundId.trim() + "_" + participant.trim()).toLowerCase()
```

Example:

```text
r1_alice
r2_bob
```

If the same key appears again, it is ignored.

---

## Tech Stack

- Java (JDK 11+)
- HttpClient (built-in)
- No external JSON library required

---

## Project Structure

```text
QuizLeaderboard/
|
|- QuizLeaderboardSystem.java
|- README.md
|- .gitignore
```

---

## How to Run

### 1. Compile

```bash
javac QuizLeaderboardSystem.java
```

### 2. Run

```bash
java QuizLeaderboardSystem
```

---

## Sample Leaderboard Output

```text
Bob -> 295
Alice -> 280
Charlie -> 260

Total Score: 835
```

---

## Submission Format (JSON)

```json
{
  "regNo": "2024CS101",
  "leaderboard": [
    { "participant": "Bob", "totalScore": 295 },
    { "participant": "Alice", "totalScore": 280 },
    { "participant": "Charlie", "totalScore": 260 }
  ]
}
```

---
##Execution Output
<img width="1033" height="771" alt="Image" src="https://github.com/user-attachments/assets/ef9afb82-0680-4704-8c15-69f2cfa60e42" />

<img width="831" height="756" alt="Image" src="https://github.com/user-attachments/assets/d624fc77-05f0-431d-b9da-1ca588f812c3" />
---

## Challenges Faced

- Handling duplicate API responses
- Ensuring correct aggregation
- Maintaining proper delay between requests
- Avoiding multiple submissions

---

## Solution Approach

1. Poll API 10 times
2. Store unique events using Set
3. Aggregate scores using Map
4. Sort leaderboard
5. Submit final result once

---

## Learning Outcomes

- Real-world API handling
- Idempotency in distributed systems
- Backend data processing
- Clean code structuring

---

## Author

Sahil Singh

---

## Note

The API may return cumulative counters like totalPollsMade and attemptCount across runs for the same regNo. This does not change the correctness of the local deduplication and leaderboard logic.
