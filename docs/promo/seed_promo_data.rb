# Development-only data for the store screenshots (issue #28). Run from the backend:
#   cd ../village-platform && bin/rails runner ../aghour-android/docs/promo/seed_promo_data.rb
# Creates a demo resident and gives the welcome article the village photos, so the 10.1.0
# features (images on news, editing the profile) have something to show. Idempotent.
abort "development only" unless Rails.env.development?

password = ENV.fetch("PROMO_PASSWORD") { SecureRandom.alphanumeric(14) }
user = User.find_or_initialize_by(phone: "01000000001")
user.update!(name: "محمد عبد الرحمن", email: "mohamed@example.com", password:)
File.write("/tmp/promo_login", "01000000001\n#{password}\n") # read by the capture step, never committed

article = Article.order(:id).first
brand = Rails.public_path.join("brand")
if article && !article.attachments.attached?
  %w[ village_photo.jpg web/scene.jpg cover.png ].each do |name|
    path = brand.join(name)
    article.attachments.attach(io: path.open("rb"), filename: path.basename.to_s,
                               content_type: name.end_with?("png") ? "image/png" : "image/jpeg")
  end
end
puts "user ##{user.id}, article ##{article&.id} with #{article&.attachments&.count} images"
