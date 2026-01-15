FILENAME = '1.txt'
K = 12000
TARGET_CHAR = 'Q'

try:
    with open(FILENAME, 'r') as f:
        s = f.read().strip()
except FileNotFoundError:
    print(f"Файл {FILENAME} не найден.")
    return
dots_indices = [i for i, char in enumerate(s) if char == '.']

valid_words_boundaries = []

for i in range(len(dots_indices) - 1):
    start_idx = dots_indices[i]
    end_idx = dots_indices[i+1]
        
        
    word = s[start_idx+1 : end_idx]
        
       
    if len(word) > 0 and TARGET_CHAR in word:
        valid_words_boundaries.append((start_idx, end_idx))


min_length = float('inf')

    
for i in range(len(valid_words_boundaries) - K + 1):
    first_word_start = valid_words_boundaries[i][0]
    last_word_end = valid_words_boundaries[i + K - 1][1]
    current_length = last_word_end - first_word_start + 1
        
    if current_length < min_length:
        min_length = current_length

print(min_length)
