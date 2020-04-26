# the clustering and glance definition was moved into the processing scripts
# now this is a much shorter pipe for reformating in how the scripts want the tobii output
library(data.table)

# processing steps from the very start, with the raw tobii output

data = read.csv(f, header=TRUE)
head(data,10)
data = data[, c(1,5,6)] # needs to change a bit depending on formatting
colnames(data) = c('t','x','y')
# relabel na to -1
data[is.na(data$x), 'x'] = -1
data[is.na(data$y), 'y'] = -1
# remove rows which match those above, so we can look at just the changes in location
data = data[(data$x != shift(data$x, 1, fill=-2)) | (data$y != shift(data$y, 1, fill=-2)), ]
nrow(data) # reduces to only a few thousand, becomes manageable
head(data)
nrow(data)
# adjust time scale, add dt column, hide the gaps
data$t = (data$t - data$t[[1]]) / 1000
data$dt = (shift(data$t, -1) - data$t)
#data$dt[1] = 0 # need to go back and do this before removing the gaps
data = data[data$x > 0, ]
data = data[data$y > 0, ]
head(data)
nrow(data)
write.csv(data, 'C:/Users/ryanw/Downloads/Internship/Collated Data/P4_S2a/P4_S2a.csv', row.names = F) # check output is correct first

# BATCH VERSION (introduced in version 14)
s = 'C:/Users/ryanw/Documents/Internship/gazeCSV/'
x = list.files(s, pattern = "\\.csv$")
for(f in x){
  print(f)
  data = read.csv(paste(s,f,sep=''), header=TRUE)
  data = data[c(1, ncol(data)-1, ncol(data))]
  colnames(data) = c('t','x','y')
  data[is.na(data$x), 'x'] = -1
  data[is.na(data$y), 'y'] = -1
  data = data[(data$x != shift(data$x, 1, fill=-2)) | (data$y != shift(data$y, 1, fill=-2)), ]
  data$t = (data$t - data$t[[1]]) / 1000
  data$dt = (shift(data$t, -1) - data$t)
  data = data[data$x > 0, ]
  data = data[data$y > 0, ]
  write.csv(data, paste(s,'processed/',f,sep=''), row.names = F) # check output is correct first
}

data = read.csv('q.csv')
head(data)
summary(data)
data$type = 0
data[(data$t < max(data$t)*0.3), "type"] = 1
data[(data$x < max(data$x)*0.2), "type"] = 2

write.csv(data, 'q2.csv', row.names=F)
