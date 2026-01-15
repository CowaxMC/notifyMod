f = open("1.txt")
f = f.readline().replace(".", ". ")
f = f.split(" ")
k = 0
a = []
print(f)
for i in range(len(f)):
    p = 0
    k = 0
    for j in range(len(f)):
        if 'Q' in f[j]:
            k+=1
        p+=len(f[j])
        if k>=12000:
            a.append(p)
            break
print(min(a))