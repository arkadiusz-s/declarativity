function plot_prediction(soln, time_step, varargin)
% function plot_prediction(soln, time_step, varargin)

[a,b]=plot(evidence(soln), time_step, varargin{:});

% Compute the predicted distribution (no rigid alignment performed)

assert(time_step>1);

t = time_step;
tb = testbed(soln);
ev = evidence(soln);
mymodel = model(soln);
oldmodel = legacy(model(soln));

s = get(soln);
xt = s.xt(:,t-1);
Pt = s.Pt{t-1};

cameras = process_options(varargin, 'cameras', 1:length(tb.calib));


for i=1:length(cameras),
  k = cameras(i);
  dt = ev.time(t)-ev.time(t-1);
  A  = oldmodel.A+oldmodel.Adt*dt;
  Q  = oldmodel.Q+oldmodel.Qdt*dt*dt;
  muX=A*xt;
  P=A*full(Pt)*A'+Q;
  if ev.visible(k, t)
    calib = tb.calib(k);
    ci = [1:2 oldmodel.ci(k,:)];
    if Pt(ci(3),ci(3))<9000, %hack hack hack
      center=approximateAngleDistribution(muX(ci),full(P(ci,ci)),...
          @fnProjection, eye(2)*mymodel.obs_var, oldmodel.param(k));
      
      % Don't perform rigid alignment
      %for j=1:length(center.p),
      %  center.mus(:,j)=c*R*center.mus(:,j)+tt;
      %  center.covs(:,:,j)=c^2*R*center.covs(:,:,j)*R';
      %end

      % Plot the position confidence bound
      subplot(a,b,i);
      hold on;
      h=plotMogError(center, 0.95, {'m'});
      hold off;
      
    end
  end
end
