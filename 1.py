f = open("1.txt")
f = f.readline()
m = f
f = f.replace(".", ". ")
f = f.split(" ")
k = 0
d = []
a = []
p=0
for i in range(len(f)):
    p+=len(f[i])
    if 'Q' in f[i]:
        d.append(p)
b = []
l=11999
for i in range(len(d)):
    p = 0
    k = 0
    if i+l<len(d):
        a.append(len(m[d[i]:d[i+l]]))

print(min(a))  

