set :application, "liquibase"
set :repository,  "git@github.com:Marketron/liquibase-sbt-plugin.git"
set :scm, :git
set :deploy_via, :remote_cache
set :use_sudo, false
set :ssh_options, { :forward_agent => true }
set :keep_releases, 5

task :staging do
  set :gateway, "msnap@63.236.65.21"
  set :branch, (ENV['branch']||ENV['BRANCH']||"develop")
  set :user, 'deploy'
  set :deploy_to, "/var/apps/liquibase"
  server "172.16.2.125", :app, :web, :primary => true
end

task :production do
  set :gateway, "msnap@63.236.65.21"
  set :branch, (ENV['branch']||ENV['BRANCH']||"master")
  set :user, 'msnap'
  set :deploy_to, "/var/www/apps/liquibase"
  server "seamobileapp01:2020", :app, :web, :primary => true
  server "seamobileapp02:2020", :app, :web
  server "seamobileapp03:2020", :app, :web
  server "seamobileapp04:2020", :app, :web
end



namespace :deploy do
  task :start do ; end
  task :stop do ; end
  task :restart, :roles => :app, :except => { :no_release => true } do
    run "#{try_sudo} touch #{File.join(current_path,'tmp','restart.txt')}"
  end
end


namespace :sbt do
  task :package_locally, :roles => :app, :except => { :no_release => true } do
    run "mkdir -p #{release_path}/target"
    run "cd #{release_path} && sbt  compile publish-local"
  end
end


after "deploy:update_code", "sbt:package_locally"

